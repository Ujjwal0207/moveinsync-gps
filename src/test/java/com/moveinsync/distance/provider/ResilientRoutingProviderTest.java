package com.moveinsync.distance.provider;

import com.moveinsync.distance.model.Coordinate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientRoutingProviderTest {

    @Test
    @DisplayName("Should trip circuit breaker after consecutive provider errors and fast-fail subsequent calls")
    void shouldTripCircuitBreakerOnProviderFailure() {
        MockRoutingProvider mock = new MockRoutingProvider();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(3)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .recordExceptions(RoutingException.class)
                .build();
        CircuitBreaker cb = CircuitBreaker.of("test-cb", config);
        ResilientRoutingProvider resilient = new ResilientRoutingProvider(mock, cb);

        Coordinate start = new Coordinate(12.9352, 77.6245);
        Coordinate end = new Coordinate(12.9716, 77.5946);

        // Fail the mock provider
        mock.setShouldFail(true);

        // Make 3 calls to breach minimumNumberOfCalls
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> resilient.calculateDrivingDistanceKm(start, end))
                    .isInstanceOf(RoutingException.class);
        }

        // Circuit should now be OPEN
        assertThat(resilient.getCircuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call fails fast without invoking underlying mock provider
        int callsBefore = mock.getCallCount();
        assertThatThrownBy(() -> resilient.calculateDrivingDistanceKm(start, end))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Circuit Breaker is OPEN");
        assertThat(mock.getCallCount()).isEqualTo(callsBefore);
    }
}
