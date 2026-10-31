package com.moveinsync.distance.provider;

import com.moveinsync.distance.model.Coordinate;

/**
 * Contract for querying road network driving distances between waypoints.
 */
public interface RoutingProvider {

    /**
     * Calculates the road driving distance in kilometers between two coordinates.
     *
     * @param origin origin coordinate
     * @param destination destination coordinate
     * @return driving distance in kilometers
     * @throws RoutingException if the provider encounters network errors, timeouts, or rate limits
     */
    double calculateDrivingDistanceKm(Coordinate origin, Coordinate destination) throws RoutingException;

    /**
     * Reports total provider calls made (for billing and cost auditing).
     */
    int getCallCount();

    /**
     * Resets call statistics.
     */
    void resetCallCount();
}
