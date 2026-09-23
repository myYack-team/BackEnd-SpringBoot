package com.myyak.service.authService.store;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.RefreshToken;
import com.myyak.repository.RefreshTokenRepository;
import com.myyak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.refresh-store.mode", havingValue = "rds", matchIfMissing = true)
public class JpaRefreshTokenSessionStore implements RefreshTokenSessionStore {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Override
    public void issue(Long userId, String token, String familyId, Instant expiresAt) {
        LocalDateTime dbExpiry = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
        refreshTokenRepository.findByUserId(userId).ifPresentOrElse(
                existing -> existing.updateToken(token, dbExpiry),
                () -> refreshTokenRepository.save(RefreshToken.builder()
                        .user(userRepository.getReferenceById(userId))
                        .token(token)
                        .expiresAt(dbExpiry)
                        .build()));
    }

    @Override
    public String currentFamily(Long userId) {
        return null;
    }

    @Override
    public void rotate(Long userId, String previousToken, String nextToken, String familyId,
                       Instant previousExpiresAt, Instant nextExpiresAt) {
        RefreshToken stored = refreshTokenRepository.findByTokenForUpdate(previousToken)
                .orElseThrow(() -> new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN));
        if (!stored.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }
        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new GeneralException(ErrorStatus.AUTH_EXPIRED_TOKEN);
        }
        stored.updateToken(nextToken, LocalDateTime.ofInstant(nextExpiresAt, ZoneId.systemDefault()));
    }

    @Override
    public void revoke(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
