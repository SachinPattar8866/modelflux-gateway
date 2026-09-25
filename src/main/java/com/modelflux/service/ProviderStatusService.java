package com.modelflux.service;

import com.modelflux.model.dto.ProviderStatusResponse;
import com.modelflux.model.enums.ProviderStatus;
import com.modelflux.provider.AIProvider;
import com.modelflux.provider.ProviderFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProviderStatusService {

    private final ProviderFactory providerFactory;
    private final ProviderRateLimitService rateLimitService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ProviderStatusService(ProviderFactory providerFactory,
                                 ProviderRateLimitService rateLimitService,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        this.providerFactory = providerFactory;
        this.rateLimitService = rateLimitService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public List<ProviderStatusResponse> getAllStatuses() {
        List<ProviderStatusResponse> results = new ArrayList<>();

        for (AIProvider provider : providerFactory.getProvidersInFallbackOrder()) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider.getProviderName().name());

            if (cb.getState() == CircuitBreaker.State.OPEN) {
                results.add(new ProviderStatusResponse(provider.getProviderName(), ProviderStatus.DOWN, null));
            } else if (rateLimitService.isBlocked(provider.getProviderName())) {
                long resetSeconds = rateLimitService.getResetSeconds(provider.getProviderName());
                results.add(new ProviderStatusResponse(provider.getProviderName(), ProviderStatus.RATE_LIMITED, resetSeconds));
            } else {
                results.add(new ProviderStatusResponse(provider.getProviderName(), ProviderStatus.ACTIVE, null));
            }
        }

        return results;
    }
}