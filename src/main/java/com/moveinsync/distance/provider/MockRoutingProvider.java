package com.moveinsync.distance.provider;

import com.moveinsync.distance.model.Coordinate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance test double for routing providers.
 * Supports failure injection, circuit breaking simulations, and predefined routing responses.
 */
public class MockRoutingProvider implements RoutingProvider {

    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile boolean shouldFail = false;
    private volatile String failureReason = "Provider 503 Service Unavailable";
    private final Map<String, Double> predefinedDistances = new HashMap<>();
    private final double defaultRoadCurvatureFactor;

    public MockRoutingProvider() {
        this(1.28); // Standard urban road network multiplier over Euclidean distance
    }

    public MockRoutingProvider(double defaultRoadCurvatureFactor) {
        this.defaultRoadCurvatureFactor = defaultRoadCurvatureFactor;
    }

    @Override
    public double calculateDrivingDistanceKm(Coordinate origin, Coordinate destination) throws RoutingException {
        callCount.incrementAndGet();

        if (shouldFail) {
            throw new RoutingException(failureReason);
        }

        String routeKey = makeKey(origin, destination);
        if (predefinedDistances.containsKey(routeKey)) {
            return predefinedDistances.get(routeKey);
        }

        // Realistic driving distance
        double aerial = origin.haversineDistanceKm(destination);
        return aerial * defaultRoadCurvatureFactor;
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setPredefinedDistance(Coordinate origin, Coordinate destination, double distanceKm) {
        predefinedDistances.put(makeKey(origin, destination), distanceKm);
    }

    private String makeKey(Coordinate origin, Coordinate destination) {
        return origin.toCellKey(3) + "->" + destination.toCellKey(3);
    }

    @Override
    public int getCallCount() {
        return callCount.get();
    }

    @Override
    public void resetCallCount() {
        callCount.set(0);
    }
}
