package com.myyak.service.authService.store;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.RefreshToken;
import com.myyak.repository.RefreshTokenRepository;
import com.myyak.util.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** One-time maintenance migration. Run only while the old application cannot issue or rotate tokens. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.refresh-store.mode", havingValue = "redis")
@ConditionalOnProperty(name = "auth.refresh-store.migrate-legacy", havingValue = "true")
public class RefreshTokenMigrationRunner implements ApplicationRunner {

    private final RefreshTokenRepository repository;
    private final RedisRefreshTokenSessionStore store;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        int migrated = 0;
        int skipped = 0;
        for (RefreshToken row : repository.findAll()) {
            Claims claims;
            try {
                claims = jwtProvider.validateAndParseRefreshToken(row.getToken());
            } catch (GeneralException e) {
                // Expired or invalid legacy tokens cannot become active again.
                skipped++;
                continue;
            }
            Long userId = row.getUser().getId();
            if (!"refresh".equals(claims.get("type", String.class))
                    || !String.valueOf(userId).equals(claims.getSubject())) {
                skipped++;
                continue;
            }
            Instant dbExpiry = row.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
            Instant expiry = dbExpiry.isBefore(claims.getExpiration().toInstant())
                    ? dbExpiry : claims.getExpiration().toInstant();
            if (!expiry.isAfter(Instant.now())) {
                skipped++;
                continue;
            }
            String family = claims.get("family", String.class);
            if (family == null) family = UUID.randomUUID().toString();
            store.issue(userId, row.getToken(), family, expiry);
            migrated++;
        }
        log.info("Refresh Token migration complete: migrated={}, skipped={}", migrated, skipped);
    }
}
