package com.moveinsync.distance.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single GPS fix recorded by an in-cab device.
 */
public record GpsPing(
        String pingId,
        Coordinate coordinate,
        Instant timestamp,
        double accuracyMeters
) implements Comparable<GpsPing> {

    public GpsPing {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (accuracyMeters < 0.0) {
            throw new IllegalArgumentException("accuracyMeters cannot be negative: " + accuracyMeters);
        }
    }

    public boolean isAccurate(double maxAccuracyThresholdMeters) {
        return accuracyMeters <= maxAccuracyThresholdMeters;
    }

    @Override
    public int compareTo(GpsPing other) {
        return this.timestamp.compareTo(other.timestamp);
    }
}
