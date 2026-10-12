package com.myyak.service.adminAuthService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.AdminAccessProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AdminAccessProperties properties;
    private final AdminSessionStore store;
    private final AdminMailSender mail;

    @Override
    public String createChallenge() {
        requireConfigured();
        String challenge = randomToken();
        String code = String.format("%09d", RANDOM.nextInt(1_000_000_000));
        if (!store.createChallenge(challenge, hash(challenge + ":" + code), Duration.ofMinutes(5))) {
            throw new GeneralException(ErrorStatus.ADMIN_RATE_LIMITED);
        }
        mail.sendLoginCode(code);
        return challenge;
    }

    @Override
    public String createSession(String challenge, String code) {
        requireConfigured();
        if (!isToken(challenge) || code == null || !code.matches("[0-9]{9}")
                || !store.consumeChallenge(challenge, hash(challenge + ":" + code))) {
            throw new GeneralException(ErrorStatus.ADMIN_CODE_INVALID);
        }
        String token = randomToken();
        store.createSession(hash(token), randomToken(), Duration.ofSeconds(properties.getSessionSeconds()));
        return token;
    }

    @Override
    public String findSession(String token) {
        if (!properties.isConfigured() || !isToken(token)) return null;
        return store.findSession(hash(token));
    }

    @Override
    public void deleteSession(String token) {
        if (isToken(token)) store.deleteSession(hash(token));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE);
    }

    public static boolean isToken(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
