package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.GpsPing;

import java.time.Duration;
import java.util.*;

/**
 * Production implementation of GpsStreamCleaner.
 * Enforces deduplication, chronological ordering, accuracy thresholds,
 * and teleport jump filtering based on implied maximum speed.
 */
public class DefaultGpsStreamCleaner implements GpsStreamCleaner {

    public static final double DEFAULT_MAX_SPEED_KMH = 120.0;
    public static final double DEFAULT_MAX_ACCURACY_METERS = 50.0;

    private final double maxSpeedKmh;
    private final double maxAccuracyMeters;

    public DefaultGpsStreamCleaner() {
        this(DEFAULT_MAX_SPEED_KMH, DEFAULT_MAX_ACCURACY_METERS);
    }

    public DefaultGpsStreamCleaner(double maxSpeedKmh, double maxAccuracyMeters) {
        if (maxSpeedKmh <= 0.0) {
            throw new IllegalArgumentException("maxSpeedKmh must be positive: " + maxSpeedKmh);
        }
        if (maxAccuracyMeters <= 0.0) {
            throw new IllegalArgumentException("maxAccuracyMeters must be positive: " + maxAccuracyMeters);
        }
        this.maxSpeedKmh = maxSpeedKmh;
        this.maxAccuracyMeters = maxAccuracyMeters;
    }

    @Override
    public CleanerResult clean(List<GpsPing> rawPings) {
        if (rawPings == null || rawPings.isEmpty()) {
            return new CleanerResult(List.of(), 0, 0, 0, 0);
        }

        int totalReceived = rawPings.size();

        // 1. Filter out nulls and group by timestamp to eliminate duplicate timestamps
        // If duplicate timestamps exist, keep the fix with the best (lowest) accuracyMeters
        Map<java.time.Instant, GpsPing> bestPingByTimestamp = new HashMap<>();
        int duplicates = 0;

        for (GpsPing ping : rawPings) {
            if (ping == null) continue;

            GpsPing existing = bestPingByTimestamp.get(ping.timestamp());
            if (existing == null) {
                bestPingByTimestamp.put(ping.timestamp(), ping);
            } else {
                duplicates++;
                // If new ping has better accuracy, replace existing
                if (ping.accuracyMeters() < existing.accuracyMeters()) {
                    bestPingByTimestamp.put(ping.timestamp(), ping);
                }
            }
        }

        // 2. Sort chronologically
        List<GpsPing> sortedPings = new ArrayList<>(bestPingByTimestamp.values());
        Collections.sort(sortedPings);

        // 3. Filter low-accuracy fixes
        List<GpsPing> accuracyFiltered = new ArrayList<>();
        int lowAccuracyDropped = 0;
        for (GpsPing ping : sortedPings) {
            if (ping.isAccurate(maxAccuracyMeters)) {
                accuracyFiltered.add(ping);
            } else {
                lowAccuracyDropped++;
            }
        }

        if (accuracyFiltered.isEmpty()) {
            return new CleanerResult(List.of(), totalReceived, duplicates, lowAccuracyDropped, 0);
        }

        // 4. Filter teleport jumps (implied speed > maxSpeedKmh)
        List<GpsPing> cleanedPings = new ArrayList<>();
        int teleportJumpsDropped = 0;

        GpsPing lastAccepted = accuracyFiltered.get(0);
        cleanedPings.add(lastAccepted);

        for (int i = 1; i < accuracyFiltered.size(); i++) {
            GpsPing candidate = accuracyFiltered.get(i);

            long elapsedMillis = Duration.between(lastAccepted.timestamp(), candidate.timestamp()).toMillis();
            if (elapsedMillis <= 0) {
                // Duplicate timestamp that somehow arrived, drop
                duplicates++;
                continue;
            }

            double distanceKm = lastAccepted.coordinate().haversineDistanceKm(candidate.coordinate());
            double hours = elapsedMillis / 3_600_000.0;
            double impliedSpeedKmh = distanceKm / hours;

            if (impliedSpeedKmh > maxSpeedKmh) {
                // Teleport jump detected! Candidate is dropped.
                // Notice: lastAccepted remains unchanged, so the next candidate is evaluated against lastAccepted.
                teleportJumpsDropped++;
            } else {
                cleanedPings.add(candidate);
                lastAccepted = candidate;
            }
        }

        return new CleanerResult(
                Collections.unmodifiableList(cleanedPings),
                totalReceived,
                duplicates,
                lowAccuracyDropped,
                teleportJumpsDropped
        );
    }

    public double getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }
}
