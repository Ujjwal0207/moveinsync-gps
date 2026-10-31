package com.moveinsync.distance.dto;

public record RecomputeResponseDto(
        ReconstructedTripDto reconstructedTrip,
        BillingDeltaDto delta
) {
}
