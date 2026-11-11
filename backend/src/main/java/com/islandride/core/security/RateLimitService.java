package com.islandride.core.security;

import com.islandride.core.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter backed by Redis with safe in-memory fallback
 * for protecting upload and public catalog endpoints against abuse.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final Map<String, WindowCounter> localFallbackCounters = new ConcurrentHashMap<>();

    public RateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                            @Value("${islandride.ratelimit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.enabled = enabled;
    }

    public void checkLimit(String bucketKey, int maxRequests, Duration window) {
        if (!enabled || maxRequests <= 0) {
            return;
        }
        long count = incrementCounter(bucketKey, window);
        if (count > maxRequests) {
            throw new RateLimitExceededException("Rate limit exceeded. Please try again shortly.");
        }
    }

    private long incrementCounter(String bucketKey, Duration window) {
        if (redisTemplate != null) {
            try {
                String fullKey = "ratelimit:" + bucketKey;
                Long value = redisTemplate.opsForValue().increment(fullKey);
                if (value != null && value == 1L) {
                    redisTemplate.expire(fullKey, window);
                }
                if (value != null) {
                    return value;
                }
            } catch (Exception ex) {
                log.debug("Redis rate limit fallback triggered for key {}: {}", bucketKey, ex.getMessage());
            }
        }
        return incrementLocalFallback(bucketKey, window);
    }

    private long incrementLocalFallback(String bucketKey, Duration window) {
        Instant now = Instant.now();
        WindowCounter counter = localFallbackCounters.compute(bucketKey, (k, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                return new WindowCounter(now.plus(window));
            }
            return existing;
        });
        return counter.count.incrementAndGet();
    }

    private static final class WindowCounter {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}

