package com.myyak.service.authService;

import com.myyak.service.authService.store.TemporaryAuthStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    static final Duration STATE_TTL = Duration.ofMinutes(10);
    static final String KEY_PREFIX = "oauth-state:";

    private final TemporaryAuthStore temporaryAuthStore;

    /**
     * Creates a new state token and stores it with the app redirect URI
     * @param appRedirectUri The app's redirect URI to store
     * @return The generated state token
     */
    public String createState(String appRedirectUri) {
        String state = UUID.randomUUID().toString();
        temporaryAuthStore.put(KEY_PREFIX + state, appRedirectUri, STATE_TTL);
        log.debug("Created OAuth state");
        return state;
    }

    /**
     * Validates and consumes a state token (one-time use)
     * @param state The state token to validate
     * @return The associated app redirect URI, or null if invalid/expired
     */
    public String validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            log.warn("Empty OAuth state provided");
            return null;
        }

        String appRedirectUri = temporaryAuthStore.consume(KEY_PREFIX + state);
        if (appRedirectUri == null) {
            log.warn("OAuth state not found, expired, or already used");
            return null;
        }

        log.debug("OAuth state validated and consumed");
        return appRedirectUri;
    }
}
