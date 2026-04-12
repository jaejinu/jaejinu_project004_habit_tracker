package com.habit.infra.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BadgeCacheService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final long WAIT_MS = 100;
    private static final int WAIT_RETRIES = 2;

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public BadgeCacheService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    public String keyFor(String token, String type, Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params == null ? Map.of() : params);
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(k).append('=').append(v);
        });
        String hash = sha1Prefix(sb.toString(), 6);
        return "badge:svg:" + token + ":" + type + ":" + hash;
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    public void put(String key, String svg) {
        redis.opsForValue().set(key, svg, TTL);
    }

    public String computeIfAbsent(String key, String type, Supplier<String> generator) {
        Tags tags = Tags.of("type", type);
        Optional<String> cached = get(key);
        if (cached.isPresent()) {
            meterRegistry.counter("badge.svg.cache.hit.total", tags).increment();
            return cached.get();
        }
        meterRegistry.counter("badge.svg.cache.miss.total", tags).increment();

        String lockKey = "badge:svg:lock:" + key;
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            for (int i = 0; i < WAIT_RETRIES; i++) {
                sleepQuietly(WAIT_MS);
                Optional<String> retry = get(key);
                if (retry.isPresent()) {
                    meterRegistry.counter("badge.svg.cache.hit.total", tags).increment();
                    return retry.get();
                }
            }
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String svg = generator.get();
            put(key, svg);
            return svg;
        } finally {
            sample.stop(meterRegistry.timer("badge.svg.generation.duration", tags));
            if (Boolean.TRUE.equals(acquired)) {
                redis.delete(lockKey);
            }
        }
    }

    private static String sha1Prefix(String input, int chars) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(chars);
            for (int i = 0; i < (chars + 1) / 2 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.substring(0, chars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
