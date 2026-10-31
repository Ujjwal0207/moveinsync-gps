package com.moveinsync.distance.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moveinsync.distance.model.Coordinate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance Decorator around a RoutingProvider.
 * Snaps start and end coordinates to spatial grid cells (~110m) and caches results in-memory with Caffeine,
 * drastically reducing third-party routing API costs and latencies.
 */
public class CachedRoutingProvider implements RoutingProvider {

    private final RoutingProvider delegate;
    private final Cache<String, Double> cache;
    private final int gridDecimalPlaces;
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    public CachedRoutingProvider(RoutingProvider delegate) {
        this(delegate, 50_000, Duration.ofHours(24), 3);
    }

    public CachedRoutingProvider(
            RoutingProvider delegate,
            long maximumSize,
            Duration expireAfterWrite,
            int gridDecimalPlaces
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate provider must not be null");
        this.gridDecimalPlaces = gridDecimalPlaces;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();
    }

    @Override
    public double calculateDrivingDistanceKm(Coordinate origin, Coordinate destination) throws RoutingException {
        String cacheKey = origin.toCellKey(gridDecimalPlaces) + "->" + destination.toCellKey(gridDecimalPlaces);

        Double cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        double distanceKm = delegate.calculateDrivingDistanceKm(origin, destination);
        cache.put(cacheKey, distanceKm);
        return distanceKm;
    }

    @Override
    public int getCallCount() {
        return delegate.getCallCount();
    }

    @Override
    public void resetCallCount() {
        delegate.resetCallCount();
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    public int getCacheHits() {
        return cacheHits.get();
    }

    public int getCacheMisses() {
        return cacheMisses.get();
    }

    public void clearCache() {
        cache.invalidateAll();
    }
}
