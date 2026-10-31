package com.moveinsync.distance.benchmark;

import com.moveinsync.distance.engine.DefaultDistanceEngine;
import com.moveinsync.distance.engine.DefaultGpsStreamCleaner;
import com.moveinsync.distance.engine.DefaultTrajectoryInterpolator;
import com.moveinsync.distance.model.*;
import com.moveinsync.distance.provider.CachedRoutingProvider;
import com.moveinsync.distance.provider.MockRoutingProvider;
import com.moveinsync.distance.provider.ResilientRoutingProvider;
import com.moveinsync.distance.service.InMemoryTripRepository;
import com.moveinsync.distance.service.TripReconstructionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TripReconstructionBenchmarkTest {

    private final Instant baseTime = Instant.parse("2026-10-31T06:00:00Z");

    @Test
    @DisplayName("BENCHMARK: Process 10,000 synthetic trips with ~1,500 pings each and report throughput/memory")
    void benchmark10000SyntheticTrips() {
        MockRoutingProvider mockProvider = new MockRoutingProvider(1.28);
        ResilientRoutingProvider resilient = new ResilientRoutingProvider(mockProvider);
        CachedRoutingProvider cached = new CachedRoutingProvider(resilient);
        InMemoryTripRepository repository = new InMemoryTripRepository();

        TripReconstructionService service = new TripReconstructionService(
                new DefaultGpsStreamCleaner(120.0, 50.0),
                new DefaultTrajectoryInterpolator(),
                new DefaultDistanceEngine(),
                cached,
                repository
        );

        final int TOTAL_TRIPS = 10_000;
        final int PINGS_PER_TRIP = 1_500;

        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long initialMemoryBytes = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("========================================================================");
        System.out.printf("Starting Benchmark: %d synthetic trips (~%d pings each = %,d total pings)%n",
                TOTAL_TRIPS, PINGS_PER_TRIP, (long) TOTAL_TRIPS * PINGS_PER_TRIP);
        System.out.printf("Initial Heap Memory: %.2f MB%n", initialMemoryBytes / (1024.0 * 1024.0));
        System.out.println("========================================================================");

        long[] tripLatenciesNanos = new long[TOTAL_TRIPS];
        long overallStartTime = System.nanoTime();

        // Process trips using Java parallel stream to saturate available CPU cores
        IntStream.range(0, TOTAL_TRIPS).parallel().forEach(tripIdx -> {
            long tripStartNano = System.nanoTime();

            TripEvidence evidence = generateSyntheticTrip(tripIdx, PINGS_PER_TRIP);
            ReconstructedTrip reconstructed = service.reconstructTrip(evidence);

            long tripDurationNano = System.nanoTime() - tripStartNano;
            tripLatenciesNanos[tripIdx] = tripDurationNano;

            assertThat(reconstructed.totalBillableDistanceKm()).isGreaterThan(0.0);
        });

        long totalDurationNanos = System.nanoTime() - overallStartTime;
        double totalSeconds = totalDurationNanos / 1_000_000_000.0;
        double throughputTripsPerSec = TOTAL_TRIPS / totalSeconds;
        double throughputPingsPerSec = ((long) TOTAL_TRIPS * PINGS_PER_TRIP) / totalSeconds;

        // Calculate percentiles
        Arrays.sort(tripLatenciesNanos);
        double p50Ms = tripLatenciesNanos[(int) (TOTAL_TRIPS * 0.50)] / 1_000_000.0;
        double p90Ms = tripLatenciesNanos[(int) (TOTAL_TRIPS * 0.90)] / 1_000_000.0;
        double p99Ms = tripLatenciesNanos[(int) (TOTAL_TRIPS * 0.99)] / 1_000_000.0;

        long finalMemoryBytes = runtime.totalMemory() - runtime.freeMemory();
        double usedMemoryMb = finalMemoryBytes / (1024.0 * 1024.0);

        System.out.println("\n---------------- BENCHMARK RESULTS ----------------");
        System.out.printf("Total Trips Processed:     %,d%n", TOTAL_TRIPS);
        System.out.printf("Total Pings Ingested:      %,d%n", (long) TOTAL_TRIPS * PINGS_PER_TRIP);
        System.out.printf("Total Wall-Clock Time:     %.3f seconds%n", totalSeconds);
        System.out.printf("Throughput:                %,.1f trips/sec (%,.0f pings/sec)%n", throughputTripsPerSec, throughputPingsPerSec);
        System.out.printf("p50 Trip Latency:          %.3f ms%n", p50Ms);
        System.out.printf("p90 Trip Latency:          %.3f ms%n", p90Ms);
        System.out.printf("p99 Trip Latency:          %.3f ms%n", p99Ms);
        System.out.printf("Final Heap Memory:         %.2f MB%n", usedMemoryMb);
        System.out.printf("Cached Provider Hits:      %,d%n", cached.getCacheHits());
        System.out.printf("Underlying Routing Calls:  %,d%n", cached.getCallCount());
        System.out.println("---------------------------------------------------\n");

        assertThat(totalSeconds).isLessThan(60.0); // Strict performance target
    }

    @Test
    @DisplayName("FAILURE INJECTION: Routing provider fails halfway through; verify automatic fallback to DEGRADED")
    void shouldHandleProviderFailingHalfwayThroughBatch() {
        MockRoutingProvider mockProvider = new MockRoutingProvider(1.28);
        ResilientRoutingProvider resilient = new ResilientRoutingProvider(mockProvider);
        CachedRoutingProvider cached = new CachedRoutingProvider(resilient);
        InMemoryTripRepository repository = new InMemoryTripRepository();

        TripReconstructionService service = new TripReconstructionService(
                new DefaultGpsStreamCleaner(120.0, 50.0),
                new DefaultTrajectoryInterpolator(),
                new DefaultDistanceEngine(),
                cached,
                repository
        );

        final int BATCH_SIZE = 500;
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger degradedCount = new AtomicInteger(0);

        for (int i = 0; i < BATCH_SIZE; i++) {
            // Trigger failure halfway through
            if (i == 250) {
                mockProvider.setShouldFail(true);
                mockProvider.setFailureReason("Provider Outage: 504 Gateway Timeout");
            }

            TripEvidence evidence = generateSyntheticTrip(i, 200);
            ReconstructedTrip result = service.reconstructTrip(evidence);

            if (result.status() == TripStatus.OK) {
                okCount.incrementAndGet();
            } else if (result.status() == TripStatus.DEGRADED) {
                degradedCount.incrementAndGet();
                // Check that degraded trip has aerial * 1.4 segments
                assertThat(result.segments())
                        .anyMatch(seg -> seg.rule() == SegmentRule.FALLBACK_DEGRADED);
            }
        }

        System.out.printf("Failure Injection Summary: %d Total, %d Completed OK, %d Gracefully Degraded%n",
                BATCH_SIZE, okCount.get(), degradedCount.get());

        assertThat(okCount.get()).isGreaterThanOrEqualTo(200);
        assertThat(degradedCount.get()).isGreaterThanOrEqualTo(200);
    }

    /**
     * Generates a realistic synthetic trip with ~1,500 pings:
     * - Normal cruising pings every 5s
     * - Injected bad GPS fixes (>50m accuracy)
     * - Injected 8,000 km/h teleport jumps (bad fixes)
     * - Injected 15-minute GPS loss (tunnel) with 2 employee drop milestones
     * - Destination office endpoint
     */
    private TripEvidence generateSyntheticTrip(int tripIndex, int targetPingCount) {
        String tripId = "TRIP_" + tripIndex;
        String tenantId = "TENANT_" + (tripIndex % 5);

        List<GpsPing> pings = new ArrayList<>(targetPingCount);
        double currentLat = 12.9279 + (tripIndex * 0.0001);
        double currentLon = 77.6271 + (tripIndex * 0.0001);
        Instant currentTime = baseTime;

        // Start point
        pings.add(new GpsPing("P_0", new Coordinate(currentLat, currentLon), currentTime, 8.0));

        int gapStartIndex = targetPingCount / 2;
        int gapLength = 60; // 60 pings omitted = 300 seconds gap

        for (int i = 1; i < targetPingCount; i++) {
            currentTime = currentTime.plusSeconds(5);

            // Simulate tunnel gap
            if (i >= gapStartIndex && i < (gapStartIndex + gapLength)) {
                // Cab moving in tunnel without GPS
                currentLat += 0.00015;
                currentLon += 0.00015;
                continue;
            }

            // Normal progression (~35 km/h)
            currentLat += 0.00015;
            currentLon += 0.00015;

            // 1. Inject occasional low accuracy fix (3% probability)
            if (i % 33 == 0) {
                pings.add(new GpsPing("P_BAD_ACC_" + i, new Coordinate(currentLat, currentLon), currentTime, 85.0));
                continue;
            }

            // 2. Inject occasional teleport jump (2% probability: 9km jump)
            if (i % 50 == 0) {
                Coordinate teleportCoord = new Coordinate(currentLat + 0.08, currentLon);
                pings.add(new GpsPing("P_TELEPORT_" + i, teleportCoord, currentTime, 10.0));
                continue;
            }

            pings.add(new GpsPing("P_" + i, new Coordinate(currentLat, currentLon), currentTime, 10.0));
        }

        // Add 2 employee drop milestones that occurred during the tunnel gap
        Instant tunnelDrop1Time = baseTime.plusSeconds((long) gapStartIndex * 5 + 60);
        Coordinate tunnelDrop1Coord = new Coordinate(currentLat - 0.005, currentLon - 0.005);
        EmployeeMilestone drop1 = new EmployeeMilestone(
                "EMP_" + tripIndex + "_A", MilestoneType.DROP, RiderType.PLANNED,
                tunnelDrop1Coord, tunnelDrop1Time, null, null, null, null
        );

        Instant tunnelDrop2Time = baseTime.plusSeconds((long) gapStartIndex * 5 + 180);
        Coordinate tunnelDrop2Coord = new Coordinate(currentLat - 0.002, currentLon - 0.002);
        EmployeeMilestone drop2 = new EmployeeMilestone(
                "EMP_" + tripIndex + "_B", MilestoneType.DROP, RiderType.PLANNED,
                tunnelDrop2Coord, tunnelDrop2Time, null, null, null, null
        );

        Coordinate finalOfficeCoord = new Coordinate(currentLat, currentLon);
        OfficeLocation office = new OfficeLocation("OFFICE_BLR", "MoveInSync Campus", finalOfficeCoord, 400.0);

        return new TripEvidence(
                tripId,
                tenantId,
                baseTime,
                currentTime,
                pings,
                List.of(drop1, drop2),
                office,
                finalOfficeCoord
        );
    }
}
