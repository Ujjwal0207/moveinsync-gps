package com.moveinsync.distance.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class CoordinateTest {

    @Test
    @DisplayName("Should calculate exact Haversine distance between Bangalore Kempegowda Airport and Electronic City")
    void shouldCalculateAccurateHaversineDistance() {
        // BLR Airport: 13.1986, 77.7066
        // Electronic City: 12.8399, 77.6770
        Coordinate blrAirport = new Coordinate(13.1986, 77.7066);
        Coordinate electronicCity = new Coordinate(12.8399, 77.6770);

        double distanceKm = blrAirport.haversineDistanceKm(electronicCity);
        // Aerial distance is approximately 40.0 km
        assertThat(distanceKm).isCloseTo(40.0, offset(1.0));
    }

    @Test
    @DisplayName("Distance to identical coordinate must be 0.0")
    void distanceToSelfShouldBeZero() {
        Coordinate point = new Coordinate(12.9716, 77.5946);
        assertThat(point.haversineDistanceMeters(point)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should snap coordinates to grid for spatial caching")
    void shouldSnapCoordinatesCorrectly() {
        Coordinate exact = new Coordinate(12.9715923, 77.5945678);
        Coordinate snapped3 = exact.snap(3);
        assertThat(snapped3.latitude()).isEqualTo(12.972);
        assertThat(snapped3.longitude()).isEqualTo(77.595);

        String cellKey = exact.toCellKey(3);
        assertThat(cellKey).isEqualTo("12.9720,77.5950");
    }

    @Test
    @DisplayName("Should reject invalid latitude and longitude ranges")
    void shouldRejectInvalidCoordinates() {
        assertThatThrownBy(() -> new Coordinate(91.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Coordinate(12.0, 181.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
