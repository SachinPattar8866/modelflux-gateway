package com.modelflux.service;

import com.modelflux.model.dto.ChatMessage;
import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.AIProvider;
import com.modelflux.provider.ProviderFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);

    private final ProviderFactory providerFactory;
    private final ProviderRateLimitService rateLimitService;
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ChatOrchestratorService(ProviderFactory providerFactory,
                                   ProviderRateLimitService rateLimitService,
                                   RetryRegistry retryRegistry,
                                   CircuitBreakerRegistry circuitBreakerRegistry) {
        this.providerFactory = providerFactory;
        this.rateLimitService = rateLimitService;
        this.retryRegistry = retryRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public ChatResult sendMessage(List<ChatMessage> history, String preferredProviderName) {
        AIProvider preferred = resolvePreferred(preferredProviderName);

        if (preferred != null && !rateLimitService.isBlocked(preferred.getProviderName())) {
            try {
                String reply = callWithResilience(preferred, history);
                return new ChatResult(reply, preferred.getProviderName());
            } catch (RuntimeException e) {
                log.warn("Preferred provider {} failed after retries: {}", preferred.getProviderName(), e.getMessage());
                return autoRoute(history, preferred.getProviderName());
            }
        }

        return autoRoute(history, null);
    }

    private ChatResult autoRoute(List<ChatMessage> history, ProviderName exclude) {
        List<AIProvider> candidates = providerFactory.getProvidersInFallbackOrder();

        RuntimeException lastFailure = null;

        for (AIProvider provider : candidates) {
            if (exclude != null && provider.getProviderName() == exclude) {
                continue;
            }
            if (rateLimitService.isBlocked(provider.getProviderName())) {
                log.info("Skipping {} — marked as rate-limited in Redis", provider.getProviderName());
                continue;
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider.getProviderName().name());
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                log.info("Skipping {} — circuit breaker is OPEN", provider.getProviderName());
                continue;
            }

            try {
                String reply = callWithResilience(provider, history);
                return new ChatResult(reply, provider.getProviderName());
            } catch (RuntimeException e) {
                log.warn("Provider {} failed after retries: {}", provider.getProviderName(), e.getMessage());
                lastFailure = e;
            }
        }

        throw new RuntimeException("All providers failed or are rate-limited", lastFailure);
    }

    private String callWithResilience(AIProvider provider, List<ChatMessage> history) {
        String name = provider.getProviderName().name();
        Retry retry = retryRegistry.retry(name);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> provider.sendMessage(history)));

        return decorated.get();
    }

    private AIProvider resolvePreferred(String preferredProviderName) {
        if (preferredProviderName == null || preferredProviderName.isBlank()
                || "AUTO".equalsIgnoreCase(preferredProviderName)) {
            return null;
        }
        try {
            ProviderName name = ProviderName.valueOf(preferredProviderName.toUpperCase());
            return providerFactory.getByName(name).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static class ChatResult {
        public final String reply;
        public final ProviderName providerUsed;

        public ChatResult(String reply, ProviderName providerUsed) {
            this.reply = reply;
            this.providerUsed = providerUsed;
        }
    }
}