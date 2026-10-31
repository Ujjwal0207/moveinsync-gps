package com.moveinsync.distance.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the designated corporate office destination with a geofenced threshold.
 */
public record OfficeLocation(
        String officeId,
        String officeName,
        Coordinate coordinate,
        double toleranceMeters
) {

    public static final double DEFAULT_OFFICE_TOLERANCE_METERS = 400.0;

    public OfficeLocation(String officeId, String officeName, Coordinate coordinate) {
        this(officeId, officeName, coordinate, DEFAULT_OFFICE_TOLERANCE_METERS);
    }

    public OfficeLocation {
        Objects.requireNonNull(officeId, "officeId must not be null");
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        if (toleranceMeters <= 0.0) {
            throw new IllegalArgumentException("toleranceMeters must be positive: " + toleranceMeters);
        }
    }

    public boolean isWithinTolerance(Coordinate target) {
        if (target == null) return false;
        return coordinate.haversineDistanceMeters(target) <= toleranceMeters;
    }

    /**
     * Resolves the office anchor point based on the 400m tolerance rule.
     *
     * @param actualTripEndPoint coordinate where driver marked trip ended
     * @param tripEndTime time when trip ended
     * @return AnchorPoint with either actual point or planned office fallback
     */
    public AnchorPoint resolveOfficeAnchor(Coordinate actualTripEndPoint, Instant tripEndTime) {
        Objects.requireNonNull(tripEndTime, "tripEndTime must not be null");

        if (actualTripEndPoint != null && isWithinTolerance(actualTripEndPoint)) {
            return new AnchorPoint(
                    actualTripEndPoint,
                    tripEndTime,
                    AnchorSource.OFFICE_ACTUAL,
                    "Office End: Within " + toleranceMeters + "m threshold of " + officeName
            );
        }

        return new AnchorPoint(
                this.coordinate,
                tripEndTime,
                AnchorSource.OFFICE_PLANNED_FALLBACK,
                "Office End: Driver ended outside " + toleranceMeters + "m; snapped to planned office " + officeName
        );
    }
}
