package com.myyak.service.authService.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisTemporaryAuthStore implements TemporaryAuthStore {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisTemporaryAuthStore(
            StringRedisTemplate redisTemplate,
            @Value("${auth.temporary-store.key-prefix}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(toRedisKey(key), value, ttl);
        } catch (DataAccessException e) {
            throw new TemporaryAuthStoreException(e);
        }
    }

    @Override
    public String consume(String key) {
        try {
            return redisTemplate.opsForValue().getAndDelete(toRedisKey(key));
        } catch (DataAccessException e) {
            throw new TemporaryAuthStoreException(e);
        }
    }

    private String toRedisKey(String key) {
        return keyPrefix + ":" + key;
    }

    private static String normalizePrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Temporary auth store key prefix must not be blank");
        }

        String normalized = keyPrefix.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
