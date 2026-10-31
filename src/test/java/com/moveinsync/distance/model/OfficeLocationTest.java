package com.moveinsync.distance.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OfficeLocationTest {

    private final Coordinate plannedOfficeCoord = new Coordinate(12.9279, 77.6271);
    private final OfficeLocation office = new OfficeLocation("OFF01", "MoveInSync HQ", plannedOfficeCoord, 400.0);
    private final Instant tripEndTime = Instant.parse("2026-10-31T09:00:00Z");

    @Test
    @DisplayName("Should accept actual trip end if within 400 meters of planned office")
    void shouldAcceptActualTripEndWithin400m() {
        // Coordinate approximately 150m away from office
        Coordinate actualDrop = new Coordinate(12.9289, 77.6275);
        double dist = plannedOfficeCoord.haversineDistanceMeters(actualDrop);
        assertThat(dist).isLessThan(400.0);

        AnchorPoint anchor = office.resolveOfficeAnchor(actualDrop, tripEndTime);
        assertThat(anchor.source()).isEqualTo(AnchorSource.OFFICE_ACTUAL);
        assertThat(anchor.coordinate()).isEqualTo(actualDrop);
    }

    @Test
    @DisplayName("Should fall back to planned office coordinate if driver ended > 400m away")
    void shouldFallbackToPlannedOfficeIfBeyond400m() {
        // Coordinate 2 km away (driver closed trip prematurely on main road)
        Coordinate prematureEnd = new Coordinate(12.9450, 77.6271);
        double dist = plannedOfficeCoord.haversineDistanceMeters(prematureEnd);
        assertThat(dist).isGreaterThan(400.0);

        AnchorPoint anchor = office.resolveOfficeAnchor(prematureEnd, tripEndTime);
        assertThat(anchor.source()).isEqualTo(AnchorSource.OFFICE_PLANNED_FALLBACK);
        assertThat(anchor.coordinate()).isEqualTo(plannedOfficeCoord);
    }
}
