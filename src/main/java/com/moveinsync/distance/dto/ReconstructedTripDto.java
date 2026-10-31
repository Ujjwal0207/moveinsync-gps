package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.ReconstructedTrip;
import com.moveinsync.distance.model.TripStatus;

import java.util.List;

public record ReconstructedTripDto(
        String tripId,
        String tenantId,
        String ruleVersion,
        double totalBillableDistanceKm,
        double naiveGpsDistanceKm,
        TripStatus status,
        List<ReconstructedSegmentDto> segments,
        int totalPingsReceived,
        int pingsCleanedCount,
        int pingsDroppedTeleportCount,
        int pingsDroppedAccuracyCount,
        int routingProviderCalls,
        int cacheHits,
        List<String> auditLogs
) {
    public static ReconstructedTripDto fromModel(ReconstructedTrip model) {
        return new ReconstructedTripDto(
                model.tripId(),
                model.tenantId(),
                model.ruleVersion(),
                model.totalBillableDistanceKm(),
                model.naiveGpsDistanceKm(),
                model.status(),
                model.segments().stream().map(ReconstructedSegmentDto::fromModel).toList(),
                model.totalPingsReceived(),
                model.pingsCleanedCount(),
                model.pingsDroppedTeleportCount(),
                model.pingsDroppedAccuracyCount(),
                model.routingProviderCalls(),
                model.cacheHits(),
                model.auditLogs()
        );
    }
}
