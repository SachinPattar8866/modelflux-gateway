package com.modelflux.config;

import com.modelflux.exception.ProviderApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.function.Predicate;

@Configuration
public class Resilience4jConfig {

    @Bean
    public RetryRegistry retryRegistry() {
        Predicate<Throwable> retryPredicate = throwable -> {
            if (throwable instanceof ProviderApiException e) {
                return e.isRetryable(); // only 429 / 5xx
            }
            return throwable instanceof WebClientRequestException; // network-level errors are retryable
        };

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3) // initial call + 2 retries
                .intervalFunction(attempt -> Duration.ofMillis(500L * (1L << (attempt - 1))).toMillis()) // 500ms, 1s, 2s
                .retryOnException(retryPredicate)
                .build();

        return RetryRegistry.of(config);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // trip if 50% of recent calls fail
                .slidingWindowSize(5)     // over the last 5 calls
                .waitDurationInOpenState(Duration.ofSeconds(30)) // stay tripped for 30s before testing again
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}