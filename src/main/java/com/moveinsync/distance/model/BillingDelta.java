package com.moveinsync.distance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Signed delta generated when recomputing a previously billed or closed trip.
 */
public record BillingDelta(
        String tripId,
        String tenantId,
        String previousRuleVersion,
        double previousDistanceKm,
        String newRuleVersion,
        double newDistanceKm,
        double signedDeltaKm,
        Instant recomputedAt,
        String reason
) {

    public BillingDelta {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(recomputedAt, "recomputedAt must not be null");

        previousDistanceKm = BigDecimal.valueOf(previousDistanceKm).setScale(3, RoundingMode.HALF_UP).doubleValue();
        newDistanceKm = BigDecimal.valueOf(newDistanceKm).setScale(3, RoundingMode.HALF_UP).doubleValue();
        signedDeltaKm = BigDecimal.valueOf(signedDeltaKm).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public static BillingDelta calculate(
            String tripId,
            String tenantId,
            String previousVersion,
            double previousDistanceKm,
            String newVersion,
            double newDistanceKm,
            String reason
    ) {
        double delta = newDistanceKm - previousDistanceKm;
        return new BillingDelta(
                tripId,
                tenantId,
                previousVersion,
                previousDistanceKm,
                newVersion,
                newDistanceKm,
                delta,
                Instant.now(),
                reason
        );
    }
}
