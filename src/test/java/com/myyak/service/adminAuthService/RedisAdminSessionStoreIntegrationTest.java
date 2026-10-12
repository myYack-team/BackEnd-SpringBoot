package com.myyak.service.adminAuthService;

import com.myyak.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TEST", matches = "true")
class RedisAdminSessionStoreIntegrationTest {
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    String prefix;
    RedisAdminSessionStore first;
    RedisAdminSessionStore second;

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"))));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
    }
    @AfterAll static void close() { if (connection != null) connection.destroy(); }
    @BeforeEach void stores() {
        prefix = "myyak:test:auth:v1:" + UUID.randomUUID();
        first = new RedisAdminSessionStore(redis, prefix);
        second = new RedisAdminSessionStore(redis, prefix);
    }

    @Test void onlyOneConcurrentCodeConsumerSucceeds() throws Exception {
        assertThat(first.createChallenge("challenge", "digest", Duration.ofMinutes(5))).isTrue();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                RedisAdminSessionStore store = i % 2 == 0 ? first : second;
                outcomes.add(pool.submit(() -> { start.await(); return store.consumeChallenge("challenge", "digest"); }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> outcome : outcomes) if (outcome.get(5, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
    }

    @Test void fiveFailedAttemptsConsumeCodeWithoutExtendingItsTtl() {
        first.createChallenge("challenge", "digest", Duration.ofMinutes(5));
        Long ttl = redis.getExpire(prefix + ":admin:challenge:challenge");
        for (int i = 0; i < 4; i++) assertThat(second.consumeChallenge("challenge", "wrong")).isFalse();
        assertThat(redis.getExpire(prefix + ":admin:challenge:challenge")).isBetween(1L, ttl);
        assertThat(second.consumeChallenge("challenge", "wrong")).isFalse();
        assertThat(first.consumeChallenge("challenge", "digest")).isFalse();
    }

    @Test void sessionsSurviveStoreRecreationExpireAndCanBeRevoked() throws Exception {
        first.createSession("hash", "csrf", Duration.ofSeconds(1));
        assertThat(second.findSession("hash")).isEqualTo("csrf");
        Thread.sleep(1150);
        assertThat(first.findSession("hash")).isNull();
        first.createSession("hash", "csrf", Duration.ofMinutes(1));
        second.deleteSession("hash");
        assertThat(first.findSession("hash")).isNull();
    }

    @Test void cooldownAndHourlyBudgetApplyAcrossInstances() {
        assertThat(first.createChallenge("one", "digest", Duration.ofMinutes(5))).isTrue();
        assertThat(second.createChallenge("two", "digest", Duration.ofMinutes(5))).isFalse();
        redis.delete(prefix + ":admin:cooldown");
        redis.opsForValue().set(prefix + ":admin:hourly", "10", Duration.ofHours(1));
        assertThat(second.createChallenge("three", "digest", Duration.ofMinutes(5))).isFalse();
    }

    @Test void unavailableRedisFailsClosed() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        LettuceConnectionFactory unavailable = new LettuceConnectionFactory("127.0.0.1", port);
        unavailable.afterPropertiesSet();
        unavailable.start();
        try {
            RedisAdminSessionStore store = new RedisAdminSessionStore(new StringRedisTemplate(unavailable), prefix);
            assertThatThrownBy(() -> store.findSession("hash")).isInstanceOf(GeneralException.class);
        } finally { unavailable.destroy(); }
    }
}
