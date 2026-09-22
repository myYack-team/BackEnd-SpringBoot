package com.myyak.service.authService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.service.authService.store.TemporaryAuthStore;
import com.myyak.service.authService.store.TemporaryAuthStoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCodeStore {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final String KEY_PREFIX = "code:";

    private final TemporaryAuthStore temporaryAuthStore;
    private final ObjectMapper objectMapper;

    public record CodeEntry(
            String accessToken,
            String refreshToken,
            boolean isNewUser,
            Boolean termsAgreed,
            Boolean privacyAgreed,
            Instant createdAt
    ) {}

    /**
     * Creates a new auth code and stores the token information
     * @param accessToken The access token to store
     * @param refreshToken The refresh token to store
     * @param isNewUser Whether this is a new user
     * @param termsAgreed Whether terms were agreed
     * @param privacyAgreed Whether privacy policy was agreed
     * @return The generated auth code
     */
    public String createCode(String accessToken, String refreshToken, boolean isNewUser,
                             Boolean termsAgreed, Boolean privacyAgreed) {
        String code = UUID.randomUUID().toString();
        CodeEntry entry = new CodeEntry(accessToken, refreshToken, isNewUser, termsAgreed, privacyAgreed, Instant.now());
        temporaryAuthStore.put(KEY_PREFIX + code, serialize(entry), CODE_TTL);
        log.debug("Created one-time auth code: isNewUser={}", isNewUser);
        return code;
    }

    /**
     * Exchanges an auth code for token information (one-time use)
     * @param code The auth code to exchange
     * @return The associated token information, or null if invalid/expired
     */
    public CodeEntry exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            log.warn("Empty auth code provided");
            return null;
        }

        String serializedEntry = temporaryAuthStore.consume(KEY_PREFIX + code);
        if (serializedEntry == null) {
            log.warn("Auth code not found, expired, or already used");
            return null;
        }

        log.debug("One-time auth code consumed");
        return deserialize(serializedEntry);
    }

    private String serialize(CodeEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new TemporaryAuthStoreException(e);
        }
    }

    private CodeEntry deserialize(String serializedEntry) {
        try {
            return objectMapper.readValue(serializedEntry, CodeEntry.class);
        } catch (JsonProcessingException e) {
            throw new TemporaryAuthStoreException(e);
        }
    }
}
