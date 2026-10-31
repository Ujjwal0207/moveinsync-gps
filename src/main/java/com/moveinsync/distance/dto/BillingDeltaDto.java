package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.BillingDelta;

import java.time.Instant;

public record BillingDeltaDto(
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
    public static BillingDeltaDto fromModel(BillingDelta model) {
        return new BillingDeltaDto(
                model.tripId(),
                model.tenantId(),
                model.previousRuleVersion(),
                model.previousDistanceKm(),
                model.newRuleVersion(),
                model.newDistanceKm(),
                model.signedDeltaKm(),
                model.recomputedAt(),
                model.reason()
        );
    }
}
