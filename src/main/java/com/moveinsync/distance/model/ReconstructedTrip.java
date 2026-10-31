package com.moveinsync.distance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Result of the distance reconstruction engine for a single trip.
 */
public record ReconstructedTrip(
        String tripId,
        String tenantId,
        String ruleVersion,
        double totalBillableDistanceKm,
        double naiveGpsDistanceKm,
        TripStatus status,
        List<ReconstructedSegment> segments,
        int totalPingsReceived,
        int pingsCleanedCount,
        int pingsDroppedTeleportCount,
        int pingsDroppedAccuracyCount,
        int routingProviderCalls,
        int cacheHits,
        List<String> auditLogs
) {

    public ReconstructedTrip {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ruleVersion, "ruleVersion must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        Objects.requireNonNull(auditLogs, "auditLogs must not be null");

        totalBillableDistanceKm = BigDecimal.valueOf(totalBillableDistanceKm)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
        naiveGpsDistanceKm = BigDecimal.valueOf(naiveGpsDistanceKm)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
