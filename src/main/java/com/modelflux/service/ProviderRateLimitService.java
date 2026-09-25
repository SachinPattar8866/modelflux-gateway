package com.modelflux.service;

import com.modelflux.model.enums.ProviderName;
import com.modelflux.provider.ratelimit.RateLimitHeaderParser.RateLimitInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ProviderRateLimitService {

    private static final String KEY_PREFIX = "ratelimit:blocked:";
    private static final int LOW_REQUEST_THRESHOLD = 1;

    private final StringRedisTemplate redisTemplate;

    public ProviderRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordRateLimitInfo(ProviderName provider, RateLimitInfo info) {
        if (info == null) {
            return; // provider doesn't expose usable headers (e.g. Gemini free tier)
        }

        String key = KEY_PREFIX + provider.name();

        if (info.remainingRequests <= LOW_REQUEST_THRESHOLD) {
            redisTemplate.opsForValue().set(key, String.valueOf(info.remainingRequests),
                    Duration.ofSeconds(Math.max(info.resetAfterSeconds, 1)));
        } else {
            redisTemplate.delete(key);
        }
    }

    public boolean isBlocked(ProviderName provider) {
        String key = KEY_PREFIX + provider.name();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    public long getResetSeconds(ProviderName provider) {
        String key = KEY_PREFIX + provider.name();
        Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }
}