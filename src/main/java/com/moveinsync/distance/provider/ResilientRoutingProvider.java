package com.moveinsync.distance.provider;

import com.moveinsync.distance.model.Coordinate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * Decorator adding Resilience4j Circuit Breaker protection around routing calls.
 * Protects downstream providers from cascading failure and trips open when error rates exceed threshold.
 */
public class ResilientRoutingProvider implements RoutingProvider {

    private final RoutingProvider delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientRoutingProvider(RoutingProvider delegate) {
        this(delegate, createDefaultCircuitBreaker());
    }

    public ResilientRoutingProvider(RoutingProvider delegate, CircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate provider must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    private static CircuitBreaker createDefaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(RoutingException.class)
                .build();
        return CircuitBreaker.of("routing-provider", config);
    }

    @Override
    public double calculateDrivingDistanceKm(Coordinate origin, Coordinate destination) throws RoutingException {
        try {
            return circuitBreaker.executeCallable(() -> delegate.calculateDrivingDistanceKm(origin, destination));
        } catch (CallNotPermittedException e) {
            throw new RoutingException("Routing Circuit Breaker is OPEN (provider failing): " + e.getMessage(), e);
        } catch (RoutingException e) {
            throw e;
        } catch (Exception e) {
            throw new RoutingException("Unexpected error during routing call: " + e.getMessage(), e);
        }
    }

    @Override
    public int getCallCount() {
        return delegate.getCallCount();
    }

    @Override
    public void resetCallCount() {
        delegate.resetCallCount();
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }
}
