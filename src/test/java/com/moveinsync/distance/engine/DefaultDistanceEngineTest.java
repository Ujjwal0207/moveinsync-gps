package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.AnchorPoint;
import com.moveinsync.distance.model.AnchorSource;
import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.ReconstructedSegment;
import com.moveinsync.distance.model.SegmentRule;
import com.moveinsync.distance.provider.MockRoutingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class DefaultDistanceEngineTest {

    private DefaultDistanceEngine engine;
    private MockRoutingProvider mockProvider;
    private final Instant baseTime = Instant.parse("2026-10-31T07:00:00Z");

    @BeforeEach
    void setUp() {
        engine = new DefaultDistanceEngine();
        mockProvider = new MockRoutingProvider();
    }

    @Test
    @DisplayName("Should use SHORT_GAP_AERIAL for consecutive fixes < 70s apart at plausible speed")
    void shouldUseShortGapAerial() {
        Coordinate p1 = new Coordinate(12.9352, 77.6245);
        // Approx 250m away in 30 seconds (~30 km/h)
        Coordinate p2 = new Coordinate(12.9370, 77.6250);

        AnchorPoint ap1 = new AnchorPoint(p1, baseTime, AnchorSource.GPS_PING, "P1");
        AnchorPoint ap2 = new AnchorPoint(p2, baseTime.plusSeconds(30), AnchorSource.GPS_PING, "P2");

        List<ReconstructedSegment> segments = engine.evaluateSegments(List.of(ap1, ap2), mockProvider);

        assertThat(segments).hasSize(1);
        ReconstructedSegment segment = segments.get(0);
        assertThat(segment.rule()).isEqualTo(SegmentRule.SHORT_GAP_AERIAL);
        assertThat(mockProvider.getCallCount()).isEqualTo(0); // Provider NOT called for cheap case
        assertThat(segment.distanceKm()).isCloseTo(p1.haversineDistanceKm(p2), offset(0.001));
    }

    @Test
    @DisplayName("Should use PARKED rule for long gaps where average speed is under 5 km/h")
    void shouldUseParkedRuleForCabWaiting() {
        Coordinate p1 = new Coordinate(12.9352, 77.6245);
        // 50 meters away after 10 minutes (cab was idling/parking outside employee home)
        Coordinate p2 = new Coordinate(12.9355, 77.6247);

        AnchorPoint ap1 = new AnchorPoint(p1, baseTime, AnchorSource.GPS_PING, "P1");
        AnchorPoint ap2 = new AnchorPoint(p2, baseTime.plusSeconds(600), AnchorSource.GPS_PING, "P2");

        List<ReconstructedSegment> segments = engine.evaluateSegments(List.of(ap1, ap2), mockProvider);

        assertThat(segments).hasSize(1);
        ReconstructedSegment segment = segments.get(0);
        assertThat(segment.rule()).isEqualTo(SegmentRule.PARKED);
        assertThat(mockProvider.getCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should query routing provider for real gaps")
    void shouldQueryRoutingProviderForRealGap() {
        Coordinate p1 = new Coordinate(12.9352, 77.6245);
        // 5 km away after 15 minutes (900 seconds)
        Coordinate p2 = new Coordinate(12.9716, 77.5946);

        AnchorPoint ap1 = new AnchorPoint(p1, baseTime, AnchorSource.GPS_PING, "P1");
        AnchorPoint ap2 = new AnchorPoint(p2, baseTime.plusSeconds(900), AnchorSource.GPS_PING, "P2");

        mockProvider.setPredefinedDistance(p1, p2, 6.8);

        List<ReconstructedSegment> segments = engine.evaluateSegments(List.of(ap1, ap2), mockProvider);

        assertThat(segments).hasSize(1);
        ReconstructedSegment segment = segments.get(0);
        assertThat(segment.rule()).isEqualTo(SegmentRule.ROUTED);
        assertThat(mockProvider.getCallCount()).isEqualTo(1);
        assertThat(segment.distanceKm()).isEqualTo(6.8);
    }

    @Test
    @DisplayName("Should reject routing distance above 100 km for a single gap")
    void shouldRejectDistanceAbove100Km() {
        Coordinate p1 = new Coordinate(12.9352, 77.6245);
        Coordinate p2 = new Coordinate(13.9716, 77.5946);

        AnchorPoint ap1 = new AnchorPoint(p1, baseTime, AnchorSource.GPS_PING, "P1");
        AnchorPoint ap2 = new AnchorPoint(p2, baseTime.plusSeconds(7200), AnchorSource.GPS_PING, "P2");

        mockProvider.setPredefinedDistance(p1, p2, 105.0); // > 100km

        List<ReconstructedSegment> segments = engine.evaluateSegments(List.of(ap1, ap2), mockProvider);

        assertThat(segments).hasSize(1);
        ReconstructedSegment segment = segments.get(0);
        assertThat(segment.rule()).isEqualTo(SegmentRule.REJECTED_SPEED);
        assertThat(segment.distanceKm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should fall back to aerial * 1.4 when routing provider fails")
    void shouldFallbackWhenRoutingProviderFails() {
        Coordinate p1 = new Coordinate(12.9352, 77.6245);
        Coordinate p2 = new Coordinate(12.9716, 77.5946);

        AnchorPoint ap1 = new AnchorPoint(p1, baseTime, AnchorSource.GPS_PING, "P1");
        AnchorPoint ap2 = new AnchorPoint(p2, baseTime.plusSeconds(900), AnchorSource.GPS_PING, "P2");

        mockProvider.setShouldFail(true);

        List<ReconstructedSegment> segments = engine.evaluateSegments(List.of(ap1, ap2), mockProvider);

        assertThat(segments).hasSize(1);
        ReconstructedSegment segment = segments.get(0);
        assertThat(segment.rule()).isEqualTo(SegmentRule.FALLBACK_DEGRADED);
        double expected = p1.haversineDistanceKm(p2) * 1.4;
        assertThat(segment.distanceKm()).isCloseTo(expected, offset(0.01));
    }
}
