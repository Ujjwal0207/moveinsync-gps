package com.moveinsync.distance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable value object representing a WGS-84 geographic coordinate (latitude, longitude).
 * Provides exact spherical distance calculation using the Haversine formula and coordinate snapping
 * for cache keys.
 */
public record Coordinate(double latitude, double longitude) {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double EARTH_RADIUS_KM = 6_371.0;

    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees: " + longitude);
        }
    }

    /**
     * Calculates the great-circle distance between this coordinate and another using the Haversine formula.
     *
     * @param other target coordinate
     * @return distance in meters
     */
    public double haversineDistanceMeters(Coordinate other) {
        Objects.requireNonNull(other, "other coordinate must not be null");

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double sinHalfDeltaLat = Math.sin(deltaLat / 2.0);
        double sinHalfDeltaLon = Math.sin(deltaLon / 2.0);

        double a = sinHalfDeltaLat * sinHalfDeltaLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfDeltaLon * sinHalfDeltaLon;

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculates the great-circle distance in kilometers.
     */
    public double haversineDistanceKm(Coordinate other) {
        return haversineDistanceMeters(other) / 1000.0;
    }

    /**
     * Snaps the coordinate to a fixed decimal precision grid for spatial cache keys.
     * 3 decimal places provides ~110m cell resolution (ideal for street junction caching).
     *
     * @param decimalPlaces number of fractional digits
     * @return snapped coordinate
     */
    public Coordinate snap(int decimalPlaces) {
        double snappedLat = BigDecimal.valueOf(latitude)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .doubleValue();
        double snappedLon = BigDecimal.valueOf(longitude)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .doubleValue();
        return new Coordinate(snappedLat, snappedLon);
    }

    public String toCellKey(int decimalPlaces) {
        Coordinate snapped = snap(decimalPlaces);
        return String.format("%.4f,%.4f", snapped.latitude(), snapped.longitude());
    }
}
