package com.moveinsync.distance.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Complete set of evidence ingested for a trip:
 * raw device pings, employee milestones, planned stops, and designated office location.
 */
public record TripEvidence(
        String tripId,
        String tenantId,
        Instant tripStartTime,
        Instant tripEndTime,
        List<GpsPing> rawPings,
        List<EmployeeMilestone> milestones,
        OfficeLocation officeLocation,
        Coordinate actualTripEndPoint
) {

    public TripEvidence {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        rawPings = rawPings == null ? List.of() : List.copyOf(rawPings);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
    }
}
