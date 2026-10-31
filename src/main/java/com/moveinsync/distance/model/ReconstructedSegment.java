package com.moveinsync.distance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Line item for every segment in the trip trajectory, providing auditability and explainability.
 */
public record ReconstructedSegment(
        int segmentIndex,
        Coordinate startCoordinate,
        Coordinate endCoordinate,
        Instant startTime,
        Instant endTime,
        long elapsedSeconds,
        double distanceKm,
        double impliedSpeedKmh,
        SegmentRule rule,
        String explanation
) {

    public ReconstructedSegment {
        Objects.requireNonNull(startCoordinate, "startCoordinate must not be null");
        Objects.requireNonNull(endCoordinate, "endCoordinate must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        Objects.requireNonNull(rule, "rule must not be null");

        // Format distance to 3 decimal places (meter precision)
        distanceKm = BigDecimal.valueOf(distanceKm)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();

        impliedSpeedKmh = BigDecimal.valueOf(impliedSpeedKmh)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
