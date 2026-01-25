package com.myyak.service.authService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AuthCodeStore {

    private static final long CODE_EXPIRY_SECONDS = 300; // 5 minutes
    private final ConcurrentHashMap<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

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
        codeStore.put(code, entry);
        log.debug("Created auth code: {} (isNewUser: {})", code, isNewUser);
        return code;
    }

    /**
     * Exchanges an auth code for token information (one-time use)
     * @param code The auth code to exchange
     * @return The associated token information, or null if invalid/expired
     */
    public CodeEntry exchangeCode(String code) {
        if (code == null) {
            log.warn("Null auth code provided");
            return null;
        }

        CodeEntry entry = codeStore.remove(code);
        if (entry == null) {
            log.warn("Auth code not found or already used: {}", code);
            return null;
        }

        Instant now = Instant.now();
        if (now.isAfter(entry.createdAt().plusSeconds(CODE_EXPIRY_SECONDS))) {
            log.warn("Auth code expired: {}", code);
            return null;
        }

        log.debug("Auth code exchanged: {}", code);
        return entry;
    }

    /**
     * Cleanup expired auth code entries every 1 minute
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredCodes() {
        Instant now = Instant.now();
        long initialSize = codeStore.size();

        codeStore.entrySet().removeIf(entry ->
                now.isAfter(entry.getValue().createdAt().plusSeconds(CODE_EXPIRY_SECONDS))
        );

        long removedCount = initialSize - codeStore.size();
        if (removedCount > 0) {
            log.info("Cleaned up {} expired auth code entries", removedCount);
        }
    }
}
