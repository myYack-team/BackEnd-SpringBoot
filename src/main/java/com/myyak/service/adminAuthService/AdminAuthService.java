package com.myyak.service.adminAuthService;

public interface AdminAuthService {
    String createChallenge();
    String createSession(String challenge, String code);
    String findSession(String token);
    void deleteSession(String token);
}
