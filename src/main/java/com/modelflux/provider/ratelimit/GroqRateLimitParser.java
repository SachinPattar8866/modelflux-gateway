package com.modelflux.provider.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GroqRateLimitParser implements RateLimitHeaderParser {

    @Override
    public RateLimitInfo parse(HttpHeaders headers) {
        String remaining = headers.getFirst("x-ratelimit-remaining-requests");
        String resetAfter = headers.getFirst("x-ratelimit-reset-requests");

        if (remaining == null) {
            return null;
        }

        int remainingRequests = parseIntSafe(remaining, Integer.MAX_VALUE);
        long resetSeconds = parseResetDuration(resetAfter);

        return new RateLimitInfo(remainingRequests, resetSeconds);
    }

    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseResetDuration(String value) {
        if (value == null) return 60;
        try {
            value = value.trim();
            if (value.endsWith("ms")) {
                double ms = Double.parseDouble(value.substring(0, value.length() - 2));
                return Math.max((long) Math.ceil(ms / 1000.0), 1);
            }
            long totalSeconds = 0;
            StringBuilder num = new StringBuilder();
            for (char c : value.toCharArray()) {
                if (Character.isDigit(c) || c == '.') {
                    num.append(c);
                } else {
                    double n = num.length() > 0 ? Double.parseDouble(num.toString()) : 0;
                    if (c == 'm') totalSeconds += (long) (n * 60);
                    else if (c == 's') totalSeconds += (long) n;
                    num.setLength(0);
                }
            }
            return totalSeconds > 0 ? totalSeconds : 60;
        } catch (Exception e) {
            return 60;
        }
    }
}