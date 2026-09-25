package com.modelflux.provider.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterRateLimitParser implements RateLimitHeaderParser {

    @Override
    public RateLimitInfo parse(HttpHeaders headers) {
        String remaining = headers.getFirst("x-ratelimit-remaining");
        String resetMillis = headers.getFirst("x-ratelimit-reset");

        if (remaining == null) {
            return null;
        }

        int remainingRequests = parseIntSafe(remaining, Integer.MAX_VALUE);
        long resetSeconds = 60;

        if (resetMillis != null) {
            try {
                long resetTimestamp = Long.parseLong(resetMillis.trim());
                long nowMillis = System.currentTimeMillis();
                long diffSeconds = (resetTimestamp - nowMillis) / 1000;
                if (diffSeconds > 0) resetSeconds = diffSeconds;
            } catch (Exception ignored) {
            }
        }

        return new RateLimitInfo(remainingRequests, resetSeconds);
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}