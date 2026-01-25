package com.myyak.service.authService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OAuthStateStore {

    private static final long STATE_EXPIRY_SECONDS = 600; // 10 minutes
    private final ConcurrentHashMap<String, StateEntry> stateStore = new ConcurrentHashMap<>();

    public record StateEntry(String appRedirectUri, Instant createdAt) {}

    /**
     * Creates a new state token and stores it with the app redirect URI
     * @param appRedirectUri The app's redirect URI to store
     * @return The generated state token
     */
    public String createState(String appRedirectUri) {
        String state = UUID.randomUUID().toString();
        StateEntry entry = new StateEntry(appRedirectUri, Instant.now());
        stateStore.put(state, entry);
        log.debug("Created OAuth state: {} for redirect URI: {}", state, appRedirectUri);
        return state;
    }

    /**
     * Validates and consumes a state token (one-time use)
     * @param state The state token to validate
     * @return The associated app redirect URI, or null if invalid/expired
     */
    public String validateAndConsume(String state) {
        if (state == null) {
            log.warn("Null state token provided");
            return null;
        }

        StateEntry entry = stateStore.remove(state);
        if (entry == null) {
            log.warn("State token not found or already used: {}", state);
            return null;
        }

        Instant now = Instant.now();
        if (now.isAfter(entry.createdAt().plusSeconds(STATE_EXPIRY_SECONDS))) {
            log.warn("State token expired: {}", state);
            return null;
        }

        log.debug("State token validated and consumed: {}", state);
        return entry.appRedirectUri();
    }

    /**
     * Cleanup expired state entries every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredStates() {
        Instant now = Instant.now();
        long initialSize = stateStore.size();

        stateStore.entrySet().removeIf(entry ->
                now.isAfter(entry.getValue().createdAt().plusSeconds(STATE_EXPIRY_SECONDS))
        );

        long removedCount = initialSize - stateStore.size();
        if (removedCount > 0) {
            log.info("Cleaned up {} expired OAuth state entries", removedCount);
        }
    }
}
