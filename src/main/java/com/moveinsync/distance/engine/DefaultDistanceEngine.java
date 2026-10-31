package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.AnchorPoint;
import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.ReconstructedSegment;
import com.moveinsync.distance.model.SegmentRule;
import com.moveinsync.distance.provider.RoutingException;
import com.moveinsync.distance.provider.RoutingProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core deterministic engine implementing MoveInSync's segment distance classification.
 */
public class DefaultDistanceEngine implements DistanceEngine {

    public static final long SHORT_GAP_THRESHOLD_SECONDS = 70L;
    public static final double PARKED_SPEED_THRESHOLD_KMH = 5.0;
    public static final double MAX_GAP_DISTANCE_KM = 100.0;
    public static final double DEFAULT_SPEED_LIMIT_KMH = 120.0;
    public static final double DEGRADED_CIRCUIT_FACTOR = 1.4;

    private final long shortGapThresholdSeconds;
    private final double parkedSpeedThresholdKmh;
    private final double maxGapDistanceKm;
    private final double maxSpeedLimitKmh;

    public DefaultDistanceEngine() {
        this(SHORT_GAP_THRESHOLD_SECONDS, PARKED_SPEED_THRESHOLD_KMH, MAX_GAP_DISTANCE_KM, DEFAULT_SPEED_LIMIT_KMH);
    }

    public DefaultDistanceEngine(
            long shortGapThresholdSeconds,
            double parkedSpeedThresholdKmh,
            double maxGapDistanceKm,
            double maxSpeedLimitKmh
    ) {
        this.shortGapThresholdSeconds = shortGapThresholdSeconds;
        this.parkedSpeedThresholdKmh = parkedSpeedThresholdKmh;
        this.maxGapDistanceKm = maxGapDistanceKm;
        this.maxSpeedLimitKmh = maxSpeedLimitKmh;
    }

    @Override
    public List<ReconstructedSegment> evaluateSegments(
            List<AnchorPoint> trajectory,
            RoutingProvider routingProvider
    ) {
        if (trajectory == null || trajectory.size() < 2) {
            return List.of();
        }

        List<ReconstructedSegment> segments = new ArrayList<>(trajectory.size() - 1);

        for (int i = 0; i < trajectory.size() - 1; i++) {
            AnchorPoint start = trajectory.get(i);
            AnchorPoint end = trajectory.get(i + 1);

            long elapsedSeconds = Math.max(1, Duration.between(start.timestamp(), end.timestamp()).toSeconds());
            double aerialDistKm = start.coordinate().haversineDistanceKm(end.coordinate());
            double hours = elapsedSeconds / 3600.0;
            double aerialSpeedKmh = aerialDistKm / hours;

            ReconstructedSegment segment;

            // 1. Cheap Case 1: Short Gap (< 70 seconds at plausible speed)
            if (elapsedSeconds < shortGapThresholdSeconds && aerialSpeedKmh <= maxSpeedLimitKmh) {
                segment = new ReconstructedSegment(
                        i + 1,
                        start.coordinate(),
                        end.coordinate(),
                        start.timestamp(),
                        end.timestamp(),
                        elapsedSeconds,
                        aerialDistKm,
                        aerialSpeedKmh,
                        SegmentRule.SHORT_GAP_AERIAL,
                        String.format("Consecutive points %ds < %ds apart at %.1f km/h; straight-line haversine used",
                                elapsedSeconds, shortGapThresholdSeconds, aerialSpeedKmh)
                );
            }
            // 2. Cheap Case 2: Parked Cab (implied speed under 5 km/h)
            else if (aerialSpeedKmh < parkedSpeedThresholdKmh) {
                segment = new ReconstructedSegment(
                        i + 1,
                        start.coordinate(),
                        end.coordinate(),
                        start.timestamp(),
                        end.timestamp(),
                        elapsedSeconds,
                        aerialDistKm,
                        aerialSpeedKmh,
                        SegmentRule.PARKED,
                        String.format("Gap of %ds with implied speed %.2f km/h < %.1f km/h (cab parked); straight-line used",
                                elapsedSeconds, aerialSpeedKmh, parkedSpeedThresholdKmh)
                );
            }
            // 3. Real Gap: Ask Routing Provider
            else {
                segment = evaluateRealGap(i + 1, start, end, elapsedSeconds, aerialDistKm, hours, routingProvider);
            }

            segments.add(segment);
        }

        return Collections.unmodifiableList(segments);
    }

    private ReconstructedSegment evaluateRealGap(
            int segmentIndex,
            AnchorPoint start,
            AnchorPoint end,
            long elapsedSeconds,
            double aerialDistKm,
            double hours,
            RoutingProvider routingProvider
    ) {
        try {
            double routedDistanceKm = routingProvider.calculateDrivingDistanceKm(start.coordinate(), end.coordinate());
            double impliedRoutedSpeedKmh = routedDistanceKm / hours;
            double impliedAerial14SpeedKmh = (aerialDistKm * DEGRADED_CIRCUIT_FACTOR) / hours;

            // Rejection Rule 1: Exceeds 100 km for single gap
            if (routedDistanceKm > maxGapDistanceKm) {
                return new ReconstructedSegment(
                        segmentIndex,
                        start.coordinate(),
                        end.coordinate(),
                        start.timestamp(),
                        end.timestamp(),
                        elapsedSeconds,
                        0.0,
                        impliedRoutedSpeedKmh,
                        SegmentRule.REJECTED_SPEED,
                        String.format("Routing distance %.2f km exceeds %.0f km threshold; rejected",
                                routedDistanceKm, maxGapDistanceKm)
                );
            }

            // Rejection Rule 2: Implied speed above limit AND (straight-line * 1.4) also above limit
            if (impliedRoutedSpeedKmh > maxSpeedLimitKmh && impliedAerial14SpeedKmh > maxSpeedLimitKmh) {
                return new ReconstructedSegment(
                        segmentIndex,
                        start.coordinate(),
                        end.coordinate(),
                        start.timestamp(),
                        end.timestamp(),
                        elapsedSeconds,
                        0.0,
                        impliedRoutedSpeedKmh,
                        SegmentRule.REJECTED_SPEED,
                        String.format("Implied routed speed %.1f km/h and aerial*1.4 speed %.1f km/h exceed speed limit %.0f km/h; rejected",
                                impliedRoutedSpeedKmh, impliedAerial14SpeedKmh, maxSpeedLimitKmh)
                );
            }

            // Normal accepted routed distance
            return new ReconstructedSegment(
                    segmentIndex,
                    start.coordinate(),
                    end.coordinate(),
                    start.timestamp(),
                    end.timestamp(),
                    elapsedSeconds,
                    routedDistanceKm,
                    impliedRoutedSpeedKmh,
                    SegmentRule.ROUTED,
                    String.format("Real gap of %ds (aerial %.2f km); driving distance returned by provider",
                            elapsedSeconds, aerialDistKm)
            );

        } catch (RoutingException e) {
            // Provider failed / circuit breaker tripped -> fallback to straight-line * 1.4
            double fallbackDistKm = aerialDistKm * DEGRADED_CIRCUIT_FACTOR;
            double fallbackSpeedKmh = fallbackDistKm / hours;

            return new ReconstructedSegment(
                    segmentIndex,
                    start.coordinate(),
                    end.coordinate(),
                    start.timestamp(),
                    end.timestamp(),
                    elapsedSeconds,
                    fallbackDistKm,
                    fallbackSpeedKmh,
                    SegmentRule.FALLBACK_DEGRADED,
                    String.format("Routing provider unavailable (%s); fallback straight-line * %.1f applied; trip marked DEGRADED",
                            e.getMessage(), DEGRADED_CIRCUIT_FACTOR)
            );
        }
    }
}
