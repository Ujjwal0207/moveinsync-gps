package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.OfficeLocation;
import jakarta.validation.constraints.NotNull;

public record OfficeLocationDto(
        @NotNull String officeId,
        @NotNull String officeName,
        double latitude,
        double longitude,
        Double toleranceMeters
) {
    public OfficeLocation toModel() {
        double tolerance = toleranceMeters != null ? toleranceMeters : OfficeLocation.DEFAULT_OFFICE_TOLERANCE_METERS;
        return new OfficeLocation(officeId, officeName, new Coordinate(latitude, longitude), tolerance);
    }
}
