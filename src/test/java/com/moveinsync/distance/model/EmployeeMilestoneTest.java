package com.moveinsync.distance.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeMilestoneTest {

    private final Coordinate actualCoord = new Coordinate(12.9352, 77.6245);
    private final Instant actualTime = Instant.parse("2026-10-31T08:15:00Z");

    private final Coordinate profileHomeCoord = new Coordinate(12.9279, 77.6271);
    private final Instant signInTime = Instant.parse("2026-10-31T08:10:00Z");

    private final Coordinate plannedCoord = new Coordinate(12.9200, 77.6100);
    private final Instant plannedTime = Instant.parse("2026-10-31T08:00:00Z");

    @Test
    @DisplayName("Actual pickup point takes highest priority if present")
    void actualPickupPointTakesPriority() {
        EmployeeMilestone milestone = new EmployeeMilestone(
                "EMP001", MilestoneType.PICKUP, RiderType.PLANNED,
                actualCoord, actualTime,
                profileHomeCoord, signInTime,
                plannedCoord, plannedTime
        );

        Optional<AnchorPoint> anchor = milestone.resolveAnchor(false);
        assertThat(anchor).isPresent();
        assertThat(anchor.get().source()).isEqualTo(AnchorSource.EMPLOYEE_ACTUAL_STOP);
        assertThat(anchor.get().coordinate()).isEqualTo(actualCoord);
        assertThat(anchor.get().timestamp()).isEqualTo(actualTime);
    }

    @Test
    @DisplayName("Ad-hoc rider falls back to profile home location and sign-in time when actual is missing")
    void adHocRiderFallsBackToProfileHome() {
        EmployeeMilestone milestone = new EmployeeMilestone(
                "EMP002", MilestoneType.PICKUP, RiderType.AD_HOC,
                null, null,
                profileHomeCoord, signInTime,
                plannedCoord, plannedTime
        );

        Optional<AnchorPoint> anchor = milestone.resolveAnchor(false);
        assertThat(anchor).isPresent();
        assertThat(anchor.get().source()).isEqualTo(AnchorSource.EMPLOYEE_FALLBACK_ADHOC_HOME);
        assertThat(anchor.get().coordinate()).isEqualTo(profileHomeCoord);
        assertThat(anchor.get().timestamp()).isEqualTo(signInTime);
    }

    @Test
    @DisplayName("Planned rider falls back to planned location and planned time when actual is missing")
    void plannedRiderFallsBackToPlannedLocation() {
        EmployeeMilestone milestone = new EmployeeMilestone(
                "EMP003", MilestoneType.DROP, RiderType.PLANNED,
                null, null,
                null, null,
                plannedCoord, plannedTime
        );

        Optional<AnchorPoint> anchor = milestone.resolveAnchor(false);
        assertThat(anchor).isPresent();
        assertThat(anchor.get().source()).isEqualTo(AnchorSource.EMPLOYEE_FALLBACK_PLANNED);
        assertThat(anchor.get().coordinate()).isEqualTo(plannedCoord);
        assertThat(anchor.get().timestamp()).isEqualTo(plannedTime);
    }

    @Test
    @DisplayName("No-show is omitted when includeNoShow flag is false")
    void noShowIsOmittedWhenFlagIsFalse() {
        EmployeeMilestone milestone = new EmployeeMilestone(
                "EMP004", MilestoneType.NO_SHOW, RiderType.PLANNED,
                actualCoord, actualTime,
                null, null,
                plannedCoord, plannedTime
        );

        Optional<AnchorPoint> anchor = milestone.resolveAnchor(false);
        assertThat(anchor).isEmpty();
    }

    @Test
    @DisplayName("No-show is included when includeNoShow flag is true")
    void noShowIsIncludedWhenFlagIsTrue() {
        EmployeeMilestone milestone = new EmployeeMilestone(
                "EMP004", MilestoneType.NO_SHOW, RiderType.PLANNED,
                actualCoord, actualTime,
                null, null,
                plannedCoord, plannedTime
        );

        Optional<AnchorPoint> anchor = milestone.resolveAnchor(true);
        assertThat(anchor).isPresent();
        assertThat(anchor.get().source()).isEqualTo(AnchorSource.NO_SHOW_ACTUAL);
    }
}
