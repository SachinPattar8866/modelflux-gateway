package com.modelflux.provider.ratelimit;

import org.springframework.http.HttpHeaders;

public interface RateLimitHeaderParser {

    RateLimitInfo parse(HttpHeaders headers);

    class RateLimitInfo {
        public final int remainingRequests;
        public final long resetAfterSeconds;

        public RateLimitInfo(int remainingRequests, long resetAfterSeconds) {
            this.remainingRequests = remainingRequests;
            this.resetAfterSeconds = resetAfterSeconds;
        }
    }
}