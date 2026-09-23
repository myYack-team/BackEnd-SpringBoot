package com.myyak.service.authService.store;

import java.time.Instant;

public interface RefreshTokenSessionStore {

    void issue(Long userId, String token, String familyId, Instant expiresAt);

    /** Returns the active family, or null when there is no session. */
    String currentFamily(Long userId);

    void rotate(Long userId, String previousToken, String nextToken, String familyId,
                Instant previousExpiresAt, Instant nextExpiresAt);

    void revoke(Long userId);
}
