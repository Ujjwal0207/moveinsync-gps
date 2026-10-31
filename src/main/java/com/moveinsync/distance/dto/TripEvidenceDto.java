package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.TripEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record TripEvidenceDto(
        @NotBlank String tripId,
        @NotBlank String tenantId,
        @NotNull Instant tripStartTime,
        @NotNull Instant tripEndTime,
        List<GpsPingDto> rawPings,
        List<EmployeeMilestoneDto> milestones,
        OfficeLocationDto officeLocation,
        Double actualTripEndLatitude,
        Double actualTripEndLongitude
) {
    public TripEvidence toModel() {
        Coordinate actualEnd = (actualTripEndLatitude != null && actualTripEndLongitude != null)
                ? new Coordinate(actualTripEndLatitude, actualTripEndLongitude) : null;

        return new TripEvidence(
                tripId,
                tenantId,
                tripStartTime,
                tripEndTime,
                rawPings == null ? List.of() : rawPings.stream().map(GpsPingDto::toModel).toList(),
                milestones == null ? List.of() : milestones.stream().map(EmployeeMilestoneDto::toModel).toList(),
                officeLocation != null ? officeLocation.toModel() : null,
                actualEnd
        );
    }
}
