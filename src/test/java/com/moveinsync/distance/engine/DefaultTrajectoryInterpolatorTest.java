package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTrajectoryInterpolatorTest {

    private DefaultTrajectoryInterpolator interpolator;
    private final Instant tripStart = Instant.parse("2026-10-31T18:00:00Z");

    @BeforeEach
    void setUp() {
        interpolator = new DefaultTrajectoryInterpolator();
    }

    @Test
    @DisplayName("Should anchor two drops that occurred during a 22-minute GPS loss in an underpass and basement")
    void shouldAnchorDropsDuring22MinuteGpsBlackout() {
        // Point before entering underpass
        Coordinate enteringUnderpass = new Coordinate(12.9352, 77.6245);
        GpsPing pingBeforeTunnel = new GpsPing("PING_BEFORE", enteringUnderpass, tripStart.plusSeconds(300), 10.0);

        // GPS lost for 22 minutes (1320 seconds)
        // Drop 1 at +600s
        Coordinate drop1Coord = new Coordinate(12.9200, 77.6300);
        EmployeeMilestone drop1 = new EmployeeMilestone(
                "EMP1", MilestoneType.DROP, RiderType.PLANNED,
                drop1Coord, tripStart.plusSeconds(600),
                null, null, null, null
        );

        // Drop 2 at +1000s (in basement)
        Coordinate drop2Coord = new Coordinate(12.9100, 77.6350);
        EmployeeMilestone drop2 = new EmployeeMilestone(
                "EMP2", MilestoneType.DROP, RiderType.PLANNED,
                drop2Coord, tripStart.plusSeconds(1000),
                null, null, null, null
        );

        // GPS regained after 22 minutes (+1620s)
        Coordinate exitingTunnel = new Coordinate(12.9000, 77.6400);
        GpsPing pingAfterTunnel = new GpsPing("PING_AFTER", exitingTunnel, tripStart.plusSeconds(1620), 10.0);

        TripEvidence evidence = new TripEvidence(
                "TRIP_LOGOUT_01", "TENANT_ABC",
                tripStart, tripStart.plusSeconds(1800),
                List.of(pingBeforeTunnel, pingAfterTunnel),
                List.of(drop1, drop2),
                null, null
        );

        List<AnchorPoint> trajectory = interpolator.interpolateTrajectory(
                List.of(pingBeforeTunnel, pingAfterTunnel),
                evidence,
                false
        );

        assertThat(trajectory).hasSize(4);
        assertThat(trajectory.get(0).source()).isEqualTo(AnchorSource.GPS_PING);
        assertThat(trajectory.get(1).source()).isEqualTo(AnchorSource.EMPLOYEE_ACTUAL_STOP);
        assertThat(trajectory.get(1).coordinate()).isEqualTo(drop1Coord);
        assertThat(trajectory.get(2).source()).isEqualTo(AnchorSource.EMPLOYEE_ACTUAL_STOP);
        assertThat(trajectory.get(2).coordinate()).isEqualTo(drop2Coord);
        assertThat(trajectory.get(3).source()).isEqualTo(AnchorSource.GPS_PING);
    }

    @Test
    @DisplayName("Should append Office anchor with 400m tolerance fallback if actual end is too far")
    void shouldAppendOfficeAnchor() {
        Coordinate plannedOffice = new Coordinate(12.9279, 77.6271);
        OfficeLocation office = new OfficeLocation("OFF1", "Bengaluru Tech Park", plannedOffice, 400.0);

        // Driver ended trip 3 km away
        Coordinate farAwayEnd = new Coordinate(12.9550, 77.6271);
        Instant tripEnd = tripStart.plusSeconds(2400);

        TripEvidence evidence = new TripEvidence(
                "TRIP_02", "TENANT_ABC",
                tripStart, tripEnd,
                List.of(), List.of(),
                office, farAwayEnd
        );

        List<AnchorPoint> trajectory = interpolator.interpolateTrajectory(List.of(), evidence, false);

        assertThat(trajectory).hasSize(1);
        AnchorPoint officeAnchor = trajectory.get(0);
        assertThat(officeAnchor.source()).isEqualTo(AnchorSource.OFFICE_PLANNED_FALLBACK);
        assertThat(officeAnchor.coordinate()).isEqualTo(plannedOffice);
    }
}
