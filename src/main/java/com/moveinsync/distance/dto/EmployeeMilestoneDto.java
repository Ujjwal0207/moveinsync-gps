package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.EmployeeMilestone;
import com.moveinsync.distance.model.MilestoneType;
import com.moveinsync.distance.model.RiderType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EmployeeMilestoneDto(
        @NotNull String employeeId,
        @NotNull MilestoneType milestoneType,
        @NotNull RiderType riderType,
        Double actualLatitude,
        Double actualLongitude,
        Instant actualTime,
        Double profileHomeLatitude,
        Double profileHomeLongitude,
        Instant signInTime,
        Double plannedLatitude,
        Double plannedLongitude,
        Instant plannedTime
) {
    public EmployeeMilestone toModel() {
        Coordinate actual = (actualLatitude != null && actualLongitude != null)
                ? new Coordinate(actualLatitude, actualLongitude) : null;
        Coordinate home = (profileHomeLatitude != null && profileHomeLongitude != null)
                ? new Coordinate(profileHomeLatitude, profileHomeLongitude) : null;
        Coordinate planned = (plannedLatitude != null && plannedLongitude != null)
                ? new Coordinate(plannedLatitude, plannedLongitude) : null;

        return new EmployeeMilestone(
                employeeId,
                milestoneType,
                riderType,
                actual,
                actualTime,
                home,
                signInTime,
                planned,
                plannedTime
        );
    }
}
