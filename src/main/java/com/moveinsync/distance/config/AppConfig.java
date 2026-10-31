package com.moveinsync.distance.config;

import com.moveinsync.distance.engine.DefaultDistanceEngine;
import com.moveinsync.distance.engine.DefaultGpsStreamCleaner;
import com.moveinsync.distance.engine.DefaultTrajectoryInterpolator;
import com.moveinsync.distance.provider.CachedRoutingProvider;
import com.moveinsync.distance.provider.MockRoutingProvider;
import com.moveinsync.distance.provider.ResilientRoutingProvider;
import com.moveinsync.distance.provider.RoutingProvider;
import com.moveinsync.distance.service.InMemoryTripRepository;
import com.moveinsync.distance.service.TripReconstructionService;
import com.moveinsync.distance.service.TripRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public TripRepository tripRepository() {
        return new InMemoryTripRepository();
    }

    @Bean
    public RoutingProvider routingProvider() {
        // Base mock provider with urban road factor 1.28
        MockRoutingProvider base = new MockRoutingProvider(1.28);
        // Wrap with Resilience4j circuit breaker
        ResilientRoutingProvider resilient = new ResilientRoutingProvider(base);
        // Wrap with Caffeine snapped-cell cache
        return new CachedRoutingProvider(resilient);
    }

    @Bean
    public TripReconstructionService tripReconstructionService(
            RoutingProvider routingProvider,
            TripRepository tripRepository
    ) {
        return new TripReconstructionService(
                new DefaultGpsStreamCleaner(120.0, 50.0),
                new DefaultTrajectoryInterpolator(),
                new DefaultDistanceEngine(),
                routingProvider,
                tripRepository
        );
    }
}
