package com.moveinsync.distance.service;

import com.moveinsync.distance.model.BillingDelta;
import com.moveinsync.distance.model.ReconstructedTrip;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store for reconstructed trips and audit deltas.
 */
public class InMemoryTripRepository implements TripRepository {

    private final Map<String, ReconstructedTrip> trips = new ConcurrentHashMap<>();
    private final Map<String, List<BillingDelta>> deltasByTrip = new ConcurrentHashMap<>();

    @Override
    public void save(ReconstructedTrip trip) {
        trips.put(trip.tripId(), trip);
    }

    @Override
    public Optional<ReconstructedTrip> findById(String tripId) {
        return Optional.ofNullable(trips.get(tripId));
    }

    @Override
    public List<ReconstructedTrip> findAllByTenantId(String tenantId) {
        return trips.values().stream()
                .filter(t -> Objects.equals(t.tenantId(), tenantId))
                .collect(Collectors.toList());
    }

    @Override
    public void saveDelta(BillingDelta delta) {
        deltasByTrip.computeIfAbsent(delta.tripId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(delta);
    }

    @Override
    public List<BillingDelta> findDeltasByTripId(String tripId) {
        List<BillingDelta> list = deltasByTrip.get(tripId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void clear() {
        trips.clear();
        deltasByTrip.clear();
    }
}
