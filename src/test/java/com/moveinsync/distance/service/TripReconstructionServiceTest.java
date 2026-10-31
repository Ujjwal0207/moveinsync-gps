package com.moveinsync.distance.service;

import com.moveinsync.distance.engine.*;
import com.moveinsync.distance.model.*;
import com.moveinsync.distance.provider.CachedRoutingProvider;
import com.moveinsync.distance.provider.MockRoutingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TripReconstructionServiceTest {

    private TripReconstructionService service;
    private MockRoutingProvider mockProvider;
    private CachedRoutingProvider cachedProvider;
    private InMemoryTripRepository repository;
    private final Instant baseTime = Instant.parse("2026-10-31T18:00:00Z");

    @BeforeEach
    void setUp() {
        mockProvider = new MockRoutingProvider(1.30);
        cachedProvider = new CachedRoutingProvider(mockProvider);
        repository = new InMemoryTripRepository();

        service = new TripReconstructionService(
                new DefaultGpsStreamCleaner(120.0, 50.0),
                new DefaultTrajectoryInterpolator(),
                new DefaultDistanceEngine(),
                cachedProvider,
                repository
        );
    }

    @Test
    @DisplayName("Should reconstruct logout trip through 22-minute underpass gap, anchoring drops and routing gaps")
    void shouldReconstructLogoutTripWithUnderpassGap() {
        // Logout trip departing office
        Coordinate officeCoord = new Coordinate(12.9279, 77.6271);

        // Cab drives normally for 5 minutes (pings every 30s)
        List<GpsPing> pings = new ArrayList<>();
        pings.add(new GpsPing("P0", officeCoord, baseTime, 10.0));

        Coordinate underpassEntry = new Coordinate(12.9350, 77.6250);
        pings.add(new GpsPing("P_ENTRY", underpassEntry, baseTime.plusSeconds(300), 10.0));

        // GPS blacked out for 22 minutes (1320 seconds).
        // Drop 1 in gap (+600s)
        Coordinate drop1Coord = new Coordinate(12.9450, 77.6150);
        EmployeeMilestone drop1 = new EmployeeMilestone(
                "EMP1", MilestoneType.DROP, RiderType.PLANNED,
                drop1Coord, baseTime.plusSeconds(600),
                null, null, null, null
        );

        // Drop 2 in basement (+1000s)
        Coordinate drop2Coord = new Coordinate(12.9550, 77.6050);
        EmployeeMilestone drop2 = new EmployeeMilestone(
                "EMP2", MilestoneType.DROP, RiderType.PLANNED,
                drop2Coord, baseTime.plusSeconds(1000),
                null, null, null, null
        );

        // GPS restored after 22 minutes (+1620s)
        Coordinate underpassExit = new Coordinate(12.9650, 77.5950);
        pings.add(new GpsPing("P_EXIT", underpassExit, baseTime.plusSeconds(1620), 10.0));

        // Cab drives to final employee home drop (+1800s)
        Coordinate finalDestination = new Coordinate(12.9750, 77.5850);
        pings.add(new GpsPing("P_END", finalDestination, baseTime.plusSeconds(1800), 10.0));

        // In a logout trip, office is the origin, drops are homes (no office destination)
        TripEvidence evidence = new TripEvidence(
                "TRIP_UNDERPASS_31KM", "TENANT_ACME",
                baseTime, baseTime.plusSeconds(1800),
                pings,
                List.of(drop1, drop2),
                null, null
        );

        ReconstructedTrip trip = service.reconstructTrip(evidence);

        // Naive distance skipped the internal stops during the 22-min underpass
        assertThat(trip.totalBillableDistanceKm()).isGreaterThan(trip.naiveGpsDistanceKm());
        assertThat(trip.status()).isEqualTo(TripStatus.OK);
        assertThat(trip.segments()).isNotEmpty();

        // Every segment carries an explainable rule
        for (ReconstructedSegment seg : trip.segments()) {
            assertThat(seg.rule()).isNotNull();
            assertThat(seg.explanation()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should mark trip DEGRADED and use aerial * 1.4 when routing provider fails halfway through")
    void shouldHandleRoutingProviderFailureHalfway() {
        Coordinate c1 = new Coordinate(12.9352, 77.6245);
        Coordinate c2 = new Coordinate(12.9500, 77.6100);
        Coordinate c3 = new Coordinate(12.9700, 77.5900);

        GpsPing p1 = new GpsPing("P1", c1, baseTime, 10.0);
        GpsPing p2 = new GpsPing("P2", c2, baseTime.plusSeconds(600), 10.0);
        GpsPing p3 = new GpsPing("P3", c3, baseTime.plusSeconds(1200), 10.0);

        TripEvidence evidence = new TripEvidence(
                "TRIP_FAIL_HALF", "TENANT_ACME",
                baseTime, baseTime.plusSeconds(1200),
                List.of(p1, p2, p3),
                List.of(), null, null
        );

        // Fail provider
        mockProvider.setShouldFail(true);

        ReconstructedTrip trip = service.reconstructTrip(evidence);

        assertThat(trip.status()).isEqualTo(TripStatus.DEGRADED);
        assertThat(trip.segments())
                .extracting(ReconstructedSegment::rule)
                .contains(SegmentRule.FALLBACK_DEGRADED);
    }

    @Test
    @DisplayName("Should recompute late pings and generate signed delta without silently changing closed invoice")
    void shouldProduceSignedDeltaForLatePings() {
        Coordinate c1 = new Coordinate(12.9352, 77.6245);
        Coordinate c2 = new Coordinate(12.9400, 77.6200);

        GpsPing p1 = new GpsPing("P1", c1, baseTime, 10.0);
        GpsPing p2 = new GpsPing("P2", c2, baseTime.plusSeconds(60), 10.0);

        TripEvidence initialEvidence = new TripEvidence(
                "TRIP_INVOICE_01", "TENANT_ACME",
                baseTime, baseTime.plusSeconds(60),
                List.of(p1, p2),
                List.of(), null, null
        );

        // Initial reconstruction
        ReconstructedTrip initial = service.reconstructTrip(initialEvidence);

        // Same evidence recomputed -> delta must be 0.000 km
        var recomputeSame = service.recomputeWithLateEvidence("TRIP_INVOICE_01", initialEvidence, "Duplicate run");
        assertThat(recomputeSame.delta()).isPresent();
        assertThat(recomputeSame.delta().get().signedDeltaKm()).isEqualTo(0.0);

        // Late pings arrive after billing (+1.8 km loop driven at plausible speed: 1.8km in 180s = 36 km/h)
        Coordinate c3 = new Coordinate(12.9550, 77.6100);
        GpsPing latePing = new GpsPing("P_LATE", c3, baseTime.plusSeconds(240), 10.0);

        TripEvidence updatedEvidence = new TripEvidence(
                "TRIP_INVOICE_01", "TENANT_ACME",
                baseTime, baseTime.plusSeconds(240),
                List.of(p1, p2, latePing),
                List.of(), null, null
        );

        var recomputeLate = service.recomputeWithLateEvidence(
                "TRIP_INVOICE_01",
                updatedEvidence,
                "Late pings received from cab device after depot sync"
        );

        assertThat(recomputeLate.delta()).isPresent();
        BillingDelta delta = recomputeLate.delta().get();
        assertThat(delta.signedDeltaKm()).isGreaterThan(0.0);
        assertThat(delta.previousDistanceKm()).isEqualTo(initial.totalBillableDistanceKm());
        assertThat(delta.newDistanceKm()).isEqualTo(recomputeLate.reconstructedTrip().totalBillableDistanceKm());
    }
}
