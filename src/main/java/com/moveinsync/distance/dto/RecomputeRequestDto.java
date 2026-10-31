package com.moveinsync.distance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecomputeRequestDto(
        @NotNull TripEvidenceDto updatedEvidence,
        @NotBlank String reason
) {
}
