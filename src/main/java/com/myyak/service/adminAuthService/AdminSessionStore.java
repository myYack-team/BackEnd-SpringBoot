package com.myyak.service.adminAuthService;

import java.time.Duration;

public interface AdminSessionStore {
    boolean createChallenge(String challenge, String codeHash, Duration ttl);
    boolean consumeChallenge(String challenge, String codeHash);
    void createSession(String sessionHash, String csrfToken, Duration ttl);
    String findSession(String sessionHash);
    void deleteSession(String sessionHash);
}
