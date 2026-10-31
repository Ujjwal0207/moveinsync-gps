package com.moveinsync.distance.service;

import com.moveinsync.distance.engine.*;
import com.moveinsync.distance.model.*;
import com.moveinsync.distance.provider.CachedRoutingProvider;
import com.moveinsync.distance.provider.RoutingProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core orchestration service for ingesting trip evidence, executing the reconstruction pipeline,
 * and producing idempotent billing deltas.
 */
public class TripReconstructionService {

    public static final String CURRENT_RULE_VERSION = "RULE_V1_0";

    private final GpsStreamCleaner cleaner;
    private final TrajectoryInterpolator interpolator;
    private final DistanceEngine distanceEngine;
    private final RoutingProvider routingProvider;
    private final TripRepository tripRepository;
    private final boolean defaultIncludeNoShows;

    public TripReconstructionService(
            GpsStreamCleaner cleaner,
            TrajectoryInterpolator interpolator,
            DistanceEngine distanceEngine,
            RoutingProvider routingProvider,
            TripRepository tripRepository
    ) {
        this(cleaner, interpolator, distanceEngine, routingProvider, tripRepository, false);
    }

    public TripReconstructionService(
            GpsStreamCleaner cleaner,
            TrajectoryInterpolator interpolator,
            DistanceEngine distanceEngine,
            RoutingProvider routingProvider,
            TripRepository tripRepository,
            boolean defaultIncludeNoShows
    ) {
        this.cleaner = Objects.requireNonNull(cleaner, "cleaner must not be null");
        this.interpolator = Objects.requireNonNull(interpolator, "interpolator must not be null");
        this.distanceEngine = Objects.requireNonNull(distanceEngine, "distanceEngine must not be null");
        this.routingProvider = Objects.requireNonNull(routingProvider, "routingProvider must not be null");
        this.tripRepository = Objects.requireNonNull(tripRepository, "tripRepository must not be null");
        this.defaultIncludeNoShows = defaultIncludeNoShows;
    }

    /**
     * Reconstructs billable trip distance from incoming evidence.
     */
    public ReconstructedTrip reconstructTrip(TripEvidence evidence) {
        return reconstructTrip(evidence, defaultIncludeNoShows);
    }

    public ReconstructedTrip reconstructTrip(TripEvidence evidence, boolean includeNoShows) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        List<String> auditLogs = new ArrayList<>();
        auditLogs.add("Trip " + evidence.tripId() + " reconstruction initiated under engine " + CURRENT_RULE_VERSION);

        // 1. Calculate naive raw GPS distance for comparison
        double naiveGpsDistanceKm = calculateNaiveDistance(evidence.rawPings());

        // 2. Clean the GPS stream
        int providerCallsBefore = routingProvider.getCallCount();
        CleanerResult cleanResult = cleaner.clean(evidence.rawPings());
        auditLogs.add(String.format("Cleaned %d raw pings: %d valid, %d duplicates dropped, %d low accuracy dropped, %d teleport jumps dropped",
                cleanResult.totalReceived(), cleanResult.cleanedPings().size(),
                cleanResult.duplicatesDropped(), cleanResult.lowAccuracyDropped(),
                cleanResult.teleportJumpsDropped()));

        // 3. Interpolate trajectory with anchor points
        List<AnchorPoint> trajectory = interpolator.interpolateTrajectory(
                cleanResult.cleanedPings(),
                evidence,
                includeNoShows
        );
        auditLogs.add(String.format("Interpolated trajectory with %d total waypoints (anchors + GPS)", trajectory.size()));

        // 4. Evaluate segment distances and classification rules
        List<ReconstructedSegment> segments = distanceEngine.evaluateSegments(trajectory, routingProvider);

        int providerCallsAfter = routingProvider.getCallCount();
        int providerCallsForTrip = providerCallsAfter - providerCallsBefore;
        int cacheHits = 0;
        if (routingProvider instanceof CachedRoutingProvider cached) {
            cacheHits = cached.getCacheHits();
        }

        // 5. Aggregate billable distance and determine overall trip status
        double totalBillableDistance = 0.0;
        TripStatus status = TripStatus.OK;

        for (ReconstructedSegment seg : segments) {
            totalBillableDistance += seg.distanceKm();
            if (seg.rule() == SegmentRule.FALLBACK_DEGRADED) {
                status = TripStatus.DEGRADED;
            } else if (seg.rule() == SegmentRule.REJECTED_SPEED && status != TripStatus.DEGRADED) {
                status = TripStatus.DISPUTED;
            }
        }

        totalBillableDistance = BigDecimal.valueOf(totalBillableDistance)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();

        auditLogs.add(String.format("Reconstruction finalized: Total Billable = %.3f km, Naive GPS = %.3f km, Status = %s, Provider Calls = %d",
                totalBillableDistance, naiveGpsDistanceKm, status, providerCallsForTrip));

        ReconstructedTrip trip = new ReconstructedTrip(
                evidence.tripId(),
                evidence.tenantId(),
                CURRENT_RULE_VERSION,
                totalBillableDistance,
                naiveGpsDistanceKm,
                status,
                segments,
                cleanResult.totalReceived(),
                cleanResult.cleanedPings().size(),
                cleanResult.teleportJumpsDropped(),
                cleanResult.lowAccuracyDropped(),
                providerCallsForTrip,
                cacheHits,
                auditLogs
        );

        tripRepository.save(trip);
        return trip;
    }

    /**
     * Idempotently recomputes a trip after late pings or revised evidence arrive,
     * producing a signed delta instead of silently mutating previous billing records.
     */
    public RecomputeResult recomputeWithLateEvidence(
            String tripId,
            TripEvidence updatedEvidence,
            String reason
    ) {
        Optional<ReconstructedTrip> existingOpt = tripRepository.findById(tripId);
        if (existingOpt.isEmpty()) {
            ReconstructedTrip fresh = reconstructTrip(updatedEvidence);
            return new RecomputeResult(fresh, Optional.empty());
        }

        ReconstructedTrip previousTrip = existingOpt.get();
        ReconstructedTrip updatedTrip = reconstructTrip(updatedEvidence);

        BillingDelta delta = BillingDelta.calculate(
                tripId,
                updatedTrip.tenantId(),
                previousTrip.ruleVersion(),
                previousTrip.totalBillableDistanceKm(),
                updatedTrip.ruleVersion(),
                updatedTrip.totalBillableDistanceKm(),
                reason
        );

        tripRepository.saveDelta(delta);
        return new RecomputeResult(updatedTrip, Optional.of(delta));
    }

    private double calculateNaiveDistance(List<GpsPing> pings) {
        if (pings == null || pings.size() < 2) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < pings.size() - 1; i++) {
            Coordinate c1 = pings.get(i).coordinate();
            Coordinate c2 = pings.get(i + 1).coordinate();
            sum += c1.haversineDistanceKm(c2);
        }
        return BigDecimal.valueOf(sum).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public record RecomputeResult(ReconstructedTrip reconstructedTrip, Optional<BillingDelta> delta) {}
}
