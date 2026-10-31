package com.moveinsync.distance.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable chronological waypoint in the reconstructed trajectory.
 * Can be derived from a cleaned GPS ping or patched from business milestones.
 */
public record AnchorPoint(
        Coordinate coordinate,
        Instant timestamp,
        AnchorSource source,
        String description
) implements Comparable<AnchorPoint> {

    public AnchorPoint {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }

    @Override
    public int compareTo(AnchorPoint other) {
        int cmp = this.timestamp.compareTo(other.timestamp);
        if (cmp != 0) {
            return cmp;
        }
        // Deterministic tie-breaker by source ordinal and description
        int srcCmp = Integer.compare(this.source.ordinal(), other.source.ordinal());
        if (srcCmp != 0) {
            return srcCmp;
        }
        return Objects.toString(this.description, "").compareTo(Objects.toString(other.description, ""));
    }
}
