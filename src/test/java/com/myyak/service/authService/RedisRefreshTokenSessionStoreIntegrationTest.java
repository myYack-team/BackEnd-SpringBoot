package com.myyak.service.authService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.service.authService.store.RedisRefreshTokenSessionStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TEST", matches = "true")
class RedisRefreshTokenSessionStoreIntegrationTest {

    private static final String HASH_KEY = "integration-test-only-hmac-key-with-at-least-32-characters";
    private static LettuceConnectionFactory firstConnection;
    private static LettuceConnectionFactory secondConnection;
    private static StringRedisTemplate firstTemplate;
    private static StringRedisTemplate secondTemplate;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        firstConnection = connection(config);
        secondConnection = connection(config);
        firstTemplate = template(firstConnection);
        secondTemplate = template(secondConnection);
    }

    @AfterAll
    static void close() {
        if (secondConnection != null) secondConnection.destroy();
        if (firstConnection != null) firstConnection.destroy();
    }

    @Test
    void storesOnlyDigestWithTtlAndRotatesAcrossConnections() {
        String prefix = prefix();
        RedisRefreshTokenSessionStore first = store(firstTemplate, prefix);
        RedisRefreshTokenSessionStore second = store(secondTemplate, prefix);
        String family = UUID.randomUUID().toString();
        Instant oldExpiry = Instant.now().plusSeconds(30);
        first.issue(1L, "old-plaintext-refresh", family, oldExpiry);

        String key = prefix + ":session:{1}";
        assertThat(firstTemplate.opsForValue().get(key)).doesNotContain("old-plaintext-refresh");
        assertThat(firstTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(28L, 30L);
        assertThat(second.currentFamily(1L)).isEqualTo(family);

        second.rotate(1L, "old-plaintext-refresh", "new-plaintext-refresh", family,
                oldExpiry, Instant.now().plusSeconds(60));
        assertThat(firstTemplate.opsForValue().get(key)).doesNotContain("new-plaintext-refresh");
        assertThat(firstTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(58L, 60L);
        assertThatThrownBy(() -> first.rotate(1L, "old-plaintext-refresh", "replay", family,
                oldExpiry, Instant.now().plusSeconds(60)))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getCode())
                        .isEqualTo(ErrorStatus.AUTH_REFRESH_TOKEN_REUSED));
        assertThat(first.currentFamily(1L)).isNull();
    }

    @Test
    void concurrentRotationsAllowOneSuccessAndRevokeFamilyOnReplay() throws Exception {
        String prefix = prefix();
        RedisRefreshTokenSessionStore first = store(firstTemplate, prefix);
        RedisRefreshTokenSessionStore second = store(secondTemplate, prefix);
        String family = UUID.randomUUID().toString();
        Instant oldExpiry = Instant.now().plusSeconds(60);
        first.issue(2L, "old", family, oldExpiry);
        int count = 24;
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(count)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                RedisRefreshTokenSessionStore store = i % 2 == 0 ? first : second;
                String nextToken = "new-" + i;
                tasks.add(() -> {
                    start.await();
                    try {
                        store.rotate(2L, "old", nextToken, family, oldExpiry, Instant.now().plusSeconds(60));
                        return true;
                    } catch (GeneralException e) {
                        return false;
                    }
                });
            }
            List<Future<Boolean>> results = tasks.stream().map(pool::submit).toList();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(first.currentFamily(2L)).isNull();
        }
    }

    @Test
    void loginReplacementAndLogoutInvalidateOldSession() {
        RedisRefreshTokenSessionStore store = store(firstTemplate, prefix());
        String firstFamily = UUID.randomUUID().toString();
        String secondFamily = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(60);
        store.issue(3L, "first", firstFamily, expiry);
        store.issue(3L, "second", secondFamily, expiry);
        assertThatThrownBy(() -> store.rotate(3L, "first", "next", firstFamily, expiry, expiry))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getCode())
                        .isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN));
        assertThat(store.currentFamily(3L)).isEqualTo(secondFamily);
        store.revoke(3L);
        assertThat(store.currentFamily(3L)).isNull();
    }

    @Test
    void unavailableRedisReturnsServiceUnavailable() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        LettuceConnectionFactory unavailableConnection = connection(
                new RedisStandaloneConfiguration("127.0.0.1", unavailablePort));
        try {
            RedisRefreshTokenSessionStore store = store(template(unavailableConnection), prefix());
            assertThatThrownBy(() -> store.issue(4L, "token", UUID.randomUUID().toString(),
                    Instant.now().plusSeconds(60)))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(error -> assertThat(((GeneralException) error).getCode())
                            .isEqualTo(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE));
        } finally {
            unavailableConnection.destroy();
        }
    }

    private static RedisRefreshTokenSessionStore store(StringRedisTemplate template, String prefix) {
        return new RedisRefreshTokenSessionStore(template, prefix, HASH_KEY);
    }

    private static String prefix() { return "myyak:test:refresh:v1:" + UUID.randomUUID(); }

    private static LettuceConnectionFactory connection(RedisStandaloneConfiguration config) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
