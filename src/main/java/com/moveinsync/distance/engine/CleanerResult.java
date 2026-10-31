package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.GpsPing;

import java.util.List;

/**
 * Audit metrics and cleaned stream resulting from GPS stream cleaning.
 */
public record CleanerResult(
        List<GpsPing> cleanedPings,
        int totalReceived,
        int duplicatesDropped,
        int lowAccuracyDropped,
        int teleportJumpsDropped
) {
}
