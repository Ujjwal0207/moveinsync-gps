package com.moveinsync.distance.provider;

import com.moveinsync.distance.model.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachedRoutingProviderTest {

    @Test
    @DisplayName("Should cache route lookups by snapped cell and avoid duplicate provider calls")
    void shouldCacheRouteLookups() throws RoutingException {
        MockRoutingProvider mock = new MockRoutingProvider();
        CachedRoutingProvider cached = new CachedRoutingProvider(mock);

        Coordinate start1 = new Coordinate(12.935201, 77.624501);
        Coordinate end1 = new Coordinate(12.971602, 77.594602);

        // First call - Cache Miss
        double dist1 = cached.calculateDrivingDistanceKm(start1, end1);
        assertThat(mock.getCallCount()).isEqualTo(1);
        assertThat(cached.getCacheHits()).isEqualTo(0);
        assertThat(cached.getCacheMisses()).isEqualTo(1);

        // Second call with slightly different coordinates snapping to the same ~110m cell
        Coordinate start2 = new Coordinate(12.935204, 77.624504);
        Coordinate end2 = new Coordinate(12.971603, 77.594604);

        double dist2 = cached.calculateDrivingDistanceKm(start2, end2);
        assertThat(dist2).isEqualTo(dist1);
        assertThat(mock.getCallCount()).isEqualTo(1); // Provider NOT called again!
        assertThat(cached.getCacheHits()).isEqualTo(1);
    }
}
