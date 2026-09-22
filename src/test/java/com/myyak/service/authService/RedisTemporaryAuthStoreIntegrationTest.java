package com.myyak.service.authService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.service.authService.store.RedisTemporaryAuthStore;
import com.myyak.service.authService.store.TemporaryAuthStoreException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TEST", matches = "true")
class RedisTemporaryAuthStoreIntegrationTest {

    private static LettuceConnectionFactory firstConnectionFactory;
    private static LettuceConnectionFactory secondConnectionFactory;
    private static StringRedisTemplate firstTemplate;
    private static StringRedisTemplate secondTemplate;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUpRedis() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);

        firstConnectionFactory = createConnectionFactory(configuration);
        secondConnectionFactory = createConnectionFactory(configuration);
        firstTemplate = createTemplate(firstConnectionFactory);
        secondTemplate = createTemplate(secondConnectionFactory);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    @AfterAll
    static void closeRedisConnections() {
        if (secondConnectionFactory != null) {
            secondConnectionFactory.destroy();
        }
        if (firstConnectionFactory != null) {
            firstConnectionFactory.destroy();
        }
    }

    @Test
    void keepsStateAndCodeAcrossStoreInstancesWithExpectedTtl() {
        String keyPrefix = uniqueKeyPrefix();
        OAuthStateStore firstStateStore = new OAuthStateStore(
                new RedisTemporaryAuthStore(firstTemplate, keyPrefix)
        );
        OAuthStateStore restartedStateStore = new OAuthStateStore(
                new RedisTemporaryAuthStore(secondTemplate, keyPrefix)
        );
        AuthCodeStore firstCodeStore = new AuthCodeStore(
                new RedisTemporaryAuthStore(firstTemplate, keyPrefix), objectMapper
        );
        AuthCodeStore restartedCodeStore = new AuthCodeStore(
                new RedisTemporaryAuthStore(secondTemplate, keyPrefix), objectMapper
        );

        String state = firstStateStore.createState("myyak://oauth/callback");
        String code = firstCodeStore.createCode("access", "refresh", true, null, false);

        assertThat(firstTemplate.getExpire(keyPrefix + ":" + OAuthStateStore.KEY_PREFIX + state, TimeUnit.SECONDS))
                .isBetween(590L, 600L);
        assertThat(firstTemplate.getExpire(keyPrefix + ":" + AuthCodeStore.KEY_PREFIX + code, TimeUnit.SECONDS))
                .isBetween(290L, 300L);
        assertThat(restartedStateStore.validateAndConsume(state)).isEqualTo("myyak://oauth/callback");
        AuthCodeStore.CodeEntry codeEntry = restartedCodeStore.exchangeCode(code);
        assertThat(codeEntry.accessToken()).isEqualTo("access");
        assertThat(codeEntry.refreshToken()).isEqualTo("refresh");
        assertThat(codeEntry.termsAgreed()).isNull();
        assertThat(codeEntry.privacyAgreed()).isFalse();
        assertThat(codeEntry.createdAt()).isNotNull();
    }

    @Test
    void getAndDeleteAllowsExactlyOneConcurrentConsumerAcrossStoreInstances() throws Exception {
        String keyPrefix = uniqueKeyPrefix();
        AuthCodeStore firstCodeStore = new AuthCodeStore(
                new RedisTemporaryAuthStore(firstTemplate, keyPrefix), objectMapper
        );
        AuthCodeStore secondCodeStore = new AuthCodeStore(
                new RedisTemporaryAuthStore(secondTemplate, keyPrefix), objectMapper
        );
        String code = firstCodeStore.createCode("access", "refresh", false, true, true);
        int consumerCount = 24;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);

        try {
            List<Callable<AuthCodeStore.CodeEntry>> tasks = new ArrayList<>();
            for (int i = 0; i < consumerCount; i++) {
                AuthCodeStore store = i % 2 == 0 ? firstCodeStore : secondCodeStore;
                tasks.add(() -> {
                    start.await();
                    return store.exchangeCode(code);
                });
            }

            List<Future<AuthCodeStore.CodeEntry>> results = tasks.stream()
                    .map(executor::submit)
                    .toList();
            start.countDown();

            long successCount = 0;
            for (Future<AuthCodeStore.CodeEntry> result : results) {
                if (result.get(5, TimeUnit.SECONDS) != null) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shortTtlActuallyExpiresInRedis() throws Exception {
        String keyPrefix = uniqueKeyPrefix();
        RedisTemporaryAuthStore store = new RedisTemporaryAuthStore(firstTemplate, keyPrefix);
        store.put("short-lived", "value", Duration.ofMillis(150));

        Thread.sleep(300);

        assertThat(store.consume("short-lived")).isNull();
    }

    @Test
    void unavailableRedisIsNotReportedAsAnInvalidCode() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        RedisStandaloneConfiguration unavailable = new RedisStandaloneConfiguration("127.0.0.1", unavailablePort);
        LettuceConnectionFactory unavailableFactory = createConnectionFactory(unavailable);
        StringRedisTemplate unavailableTemplate = createTemplate(unavailableFactory);
        RedisTemporaryAuthStore store = new RedisTemporaryAuthStore(unavailableTemplate, uniqueKeyPrefix());

        try {
            assertThatThrownBy(() -> store.put("key", "value", Duration.ofMinutes(1)))
                    .isInstanceOf(TemporaryAuthStoreException.class)
                    .satisfies(error -> assertThat(((TemporaryAuthStoreException) error).getCode())
                            .isEqualTo(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE));
        } finally {
            unavailableFactory.destroy();
        }
    }

    private static LettuceConnectionFactory createConnectionFactory(RedisStandaloneConfiguration configuration) {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    private static StringRedisTemplate createTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private static String uniqueKeyPrefix() {
        return "myyak:test:auth:v1:" + UUID.randomUUID();
    }
}
