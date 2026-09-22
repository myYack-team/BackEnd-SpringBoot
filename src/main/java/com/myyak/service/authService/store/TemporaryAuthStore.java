package com.myyak.service.authService.store;

import java.time.Duration;

/**
 * Short-lived authentication data store.
 *
 * <p>Values are created with a TTL and consumed atomically so OAuth state and
 * one-time authentication codes cannot be reused.</p>
 */
public interface TemporaryAuthStore {

    void put(String key, String value, Duration ttl);

    String consume(String key);
}
