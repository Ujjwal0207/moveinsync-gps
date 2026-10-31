package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.GpsPing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGpsStreamCleanerTest {

    private DefaultGpsStreamCleaner cleaner;
    private final Instant baseTime = Instant.parse("2026-10-31T10:00:00Z");

    @BeforeEach
    void setUp() {
        cleaner = new DefaultGpsStreamCleaner(120.0, 50.0);
    }

    @Test
    @DisplayName("Should remove duplicate pings with same timestamp and keep more accurate fix")
    void shouldDeduplicatePingsWithSameTimestamp() {
        Coordinate coord1 = new Coordinate(12.9716, 77.5946);
        Coordinate coord2 = new Coordinate(12.9717, 77.5947);

        GpsPing ping1 = new GpsPing("P1", coord1, baseTime, 45.0);
        GpsPing ping2 = new GpsPing("P2", coord2, baseTime, 12.0); // Better accuracy

        CleanerResult result = cleaner.clean(List.of(ping1, ping2));

        assertThat(result.totalReceived()).isEqualTo(2);
        assertThat(result.duplicatesDropped()).isEqualTo(1);
        assertThat(result.cleanedPings()).hasSize(1);
        assertThat(result.cleanedPings().get(0).pingId()).isEqualTo("P2");
    }

    @Test
    @DisplayName("Should sort out-of-order pings chronologically")
    void shouldSortOutOfOrderPings() {
        Coordinate c = new Coordinate(12.9716, 77.5946);
        GpsPing p1 = new GpsPing("P1", c, baseTime.plusSeconds(30), 10.0);
        GpsPing p2 = new GpsPing("P2", c, baseTime, 10.0);
        GpsPing p3 = new GpsPing("P3", c, baseTime.plusSeconds(15), 10.0);

        CleanerResult result = cleaner.clean(List.of(p1, p2, p3));

        assertThat(result.cleanedPings())
                .extracting(GpsPing::pingId)
                .containsExactly("P2", "P3", "P1");
    }

    @Test
    @DisplayName("Should drop fixes with accuracy exceeding the threshold")
    void shouldDropLowAccuracyFixes() {
        Coordinate c = new Coordinate(12.9716, 77.5946);
        GpsPing good = new GpsPing("GOOD", c, baseTime, 15.0);
        GpsPing bad = new GpsPing("BAD", c, baseTime.plusSeconds(10), 120.0); // 120m accuracy > 50m

        CleanerResult result = cleaner.clean(List.of(good, bad));

        assertThat(result.lowAccuracyDropped()).isEqualTo(1);
        assertThat(result.cleanedPings()).hasSize(1);
        assertThat(result.cleanedPings().get(0).pingId()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("Should drop 8,000 km/h teleport jump burst (9km jump in 4 seconds)")
    void shouldDropTeleportJumpBurstAsPerCaseStudy() {
        // Normal origin in Bangalore Koramangala
        Coordinate koramangala = new Coordinate(12.9352, 77.6245);
        GpsPing p1 = new GpsPing("NORMAL_1", koramangala, baseTime, 10.0);

        // 9km jump in 4 seconds (implied speed ~8,100 km/h)
        // 9km away is approx 0.08 degrees lat
        Coordinate glitchLocation = new Coordinate(12.9352 + 0.081, 77.6245);
        GpsPing glitch1 = new GpsPing("GLITCH_1", glitchLocation, baseTime.plusSeconds(1), 15.0);
        GpsPing glitch2 = new GpsPing("GLITCH_2", glitchLocation, baseTime.plusSeconds(2), 15.0);
        GpsPing glitch3 = new GpsPing("GLITCH_3", glitchLocation, baseTime.plusSeconds(4), 15.0);

        // Subsequent normal ping 100 meters away at 15 seconds (24 km/h plausible speed)
        Coordinate normalNext = new Coordinate(12.9360, 77.6245);
        GpsPing p2 = new GpsPing("NORMAL_2", normalNext, baseTime.plusSeconds(15), 10.0);

        CleanerResult result = cleaner.clean(List.of(p1, glitch1, glitch2, glitch3, p2));

        assertThat(result.teleportJumpsDropped()).isEqualTo(3);
        assertThat(result.cleanedPings())
                .extracting(GpsPing::pingId)
                .containsExactly("NORMAL_1", "NORMAL_2");
    }
}
