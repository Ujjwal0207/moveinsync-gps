package com.moveinsync.distance.service;

import com.moveinsync.distance.model.BillingDelta;
import com.moveinsync.distance.model.ReconstructedTrip;

import java.util.List;
import java.util.Optional;

public interface TripRepository {

    void save(ReconstructedTrip trip);

    Optional<ReconstructedTrip> findById(String tripId);

    List<ReconstructedTrip> findAllByTenantId(String tenantId);

    void saveDelta(BillingDelta delta);

    List<BillingDelta> findDeltasByTripId(String tripId);
}
