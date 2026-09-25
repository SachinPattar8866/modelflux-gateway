package com.modelflux.provider.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GeminiRateLimitParser implements RateLimitHeaderParser {

    @Override
    public RateLimitInfo parse(HttpHeaders headers) {
        // Gemini's free tier does not reliably expose standardized rate-limit headers.
        // Returning null here means ProviderRateLimitService treats Gemini as always
        // "not blocked" from a header-parsing standpoint — Resilience4j's circuit
        // breaker becomes the real safety net for this provider instead.
        return null;
    }
}