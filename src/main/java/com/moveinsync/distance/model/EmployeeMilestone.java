package com.moveinsync.distance.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents a passenger event during a cab trip.
 */
public record EmployeeMilestone(
        String employeeId,
        MilestoneType milestoneType,
        RiderType riderType,
        Coordinate actualCoordinate,
        Instant actualTime,
        Coordinate profileHomeCoordinate,
        Instant signInTime,
        Coordinate plannedCoordinate,
        Instant plannedTime
) {

    /**
     * Resolves the anchor point according to the MoveInSync fallback priority rules.
     *
     * @param includeNoShowAnchors configuration flag to control whether no-shows are anchored
     * @return Resolved AnchorPoint or empty if no-show is excluded or no valid point exists
     */
    public Optional<AnchorPoint> resolveAnchor(boolean includeNoShowAnchors) {
        if (milestoneType == MilestoneType.NO_SHOW) {
            if (!includeNoShowAnchors) {
                return Optional.empty();
            }
            if (actualCoordinate != null && actualTime != null) {
                return Optional.of(new AnchorPoint(
                        actualCoordinate,
                        actualTime,
                        AnchorSource.NO_SHOW_ACTUAL,
                        "Employee " + employeeId + " No-Show Actual Location"
                ));
            } else if (plannedCoordinate != null && plannedTime != null) {
                return Optional.of(new AnchorPoint(
                        plannedCoordinate,
                        plannedTime,
                        AnchorSource.NO_SHOW_PLANNED,
                        "Employee " + employeeId + " No-Show Planned Fallback"
                ));
            }
            return Optional.empty();
        }

        // Rider travelled (PICKUP or DROP)
        // 1. Try actual point
        if (actualCoordinate != null && actualTime != null) {
            return Optional.of(new AnchorPoint(
                    actualCoordinate,
                    actualTime,
                    AnchorSource.EMPLOYEE_ACTUAL_STOP,
                    "Employee " + employeeId + " " + milestoneType + " Actual Point"
            ));
        }

        // 2. Fall back according to rider type
        if (riderType == RiderType.AD_HOC) {
            if (profileHomeCoordinate != null && signInTime != null) {
                return Optional.of(new AnchorPoint(
                        profileHomeCoordinate,
                        signInTime,
                        AnchorSource.EMPLOYEE_FALLBACK_ADHOC_HOME,
                        "Employee " + employeeId + " Ad-Hoc Profile Home Fallback"
                ));
            }
        }

        // 3. Fall back to planned location and planned time
        if (plannedCoordinate != null && plannedTime != null) {
            return Optional.of(new AnchorPoint(
                    plannedCoordinate,
                    plannedTime,
                    AnchorSource.EMPLOYEE_FALLBACK_PLANNED,
                    "Employee " + employeeId + " Planned Schedule Fallback"
            ));
        }

        return Optional.empty();
    }
}
