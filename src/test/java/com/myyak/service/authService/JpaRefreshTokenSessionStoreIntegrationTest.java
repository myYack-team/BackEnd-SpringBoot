package com.myyak.service.authService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.User;
import com.myyak.repository.RefreshTokenRepository;
import com.myyak.repository.UserRepository;
import com.myyak.service.authService.store.JpaRefreshTokenSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaRefreshTokenSessionStore.class)
class JpaRefreshTokenSessionStoreIntegrationTest {

    @Autowired private JpaRefreshTokenSessionStore store;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private UserRepository users;

    @Test
    void issuesRotatesAndRevokesSessionInDefaultRdsMode() {
        Long userId = users.save(User.builder()
                .kakaoId("test-" + UUID.randomUUID())
                .name("test-user")
                .build()).getId();
        Instant expiry = Instant.now().plusSeconds(60);

        store.issue(userId, "initial-token", "family", expiry);
        assertThat(tokens.findByToken("initial-token")).isPresent();

        store.rotate(userId, "initial-token", "rotated-token", "family", expiry, expiry.plusSeconds(60));
        assertThat(tokens.findByToken("initial-token")).isEmpty();
        assertThat(tokens.findByToken("rotated-token")).isPresent();
        assertThatThrownBy(() -> store.rotate(userId, "initial-token", "replayed-token",
                "family", expiry, expiry.plusSeconds(60)))
                .isInstanceOf(GeneralException.class);

        store.revoke(userId);
        assertThat(tokens.findByToken("rotated-token")).isEmpty();
    }
}
