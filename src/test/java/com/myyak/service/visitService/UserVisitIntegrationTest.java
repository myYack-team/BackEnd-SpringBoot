package com.myyak.service.visitService;

import com.myyak.domain.User;
import com.myyak.config.AsyncConfig;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserVisitRepository;
import com.myyak.service.authService.store.RefreshTokenSessionStore;
import com.myyak.service.oAuthService.kakaoService.KakaoOAuthService;
import com.myyak.service.userService.UserService;
import com.myyak.service.userService.UserServiceImpl;
import com.myyak.util.PhoneHashUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserVisitIntegrationTest.Services.class, UserServiceImpl.class, AsyncConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserVisitIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        boolean mysql = "true".equals(System.getenv("MYSQL_INTEGRATION_TEST"));
        String url = mysql ? System.getenv().getOrDefault("MYSQL_TEST_URL",
                "jdbc:mysql://127.0.0.1:13306/myyak_incident_test?serverTimezone=Asia/Seoul")
                : "jdbc:h2:mem:visit-fixture;DB_CLOSE_DELAY=-1";
        if (mysql && !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/myyak_incident_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Only the disposable loopback test database is permitted");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> mysql ? System.getenv().getOrDefault("MYSQL_TEST_USER", "test") : "sa");
        properties.add("spring.datasource.password", () -> mysql ? System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "fixture-password") : "");
        properties.add("spring.datasource.driver-class-name", () -> mysql ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver");
    }

    @Autowired VisitService service;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired UserVisitRepository visits;
    @Autowired MutableClock clock;
    @Autowired VisitTrackingService tracking;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean RefreshTokenSessionStore refreshTokens;
    @MockitoBean KakaoOAuthService kakao;
    @MockitoBean PhoneHashUtil phoneHash;
    User first, second;

    @BeforeEach void prepare() {
        clock.at.set(Instant.parse("2026-10-12T15:00:00Z"));
        first = users.save(User.builder().kakaoId("test_visit_first").name("visit-first").build());
        second = users.save(User.builder().kakaoId("test_visit_second").name("visit-second").build());
    }
    @AfterEach void clean() { visits.deleteAll(); users.deleteAll(); }

    @Test void concurrentSameAccountIsCountedOnceAndDifferentAccountsCountSeparately() throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        try {
            for (int index = 0; index < 6; index++) jobs.add(executor.submit(() -> {
                start.await(); service.createVisit(first.getId(), LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))); return null;
            }));
            start.countDown();
            for (Future<?> job : jobs) job.get(30, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        service.createVisit(second.getId(), LocalDate.of(2026, 10, 13));
        assertThat(visits.count()).isEqualTo(2);
        assertThat(service.getVisitSummary().getTodayVisitors()).isEqualTo(2);
    }

    @Test void midnightCreatesANewDayAndSummaryCountsDistinctAccountDays() {
        clock.at.set(Instant.parse("2026-10-12T14:59:59Z"));
        service.createVisit(first.getId(), LocalDate.of(2026, 10, 12));
        service.createVisit(first.getId(), LocalDate.of(2026, 10, 12));
        clock.at.set(Instant.parse("2026-10-12T15:00:00Z"));
        service.createVisit(first.getId(), LocalDate.of(2026, 10, 13));
        service.createVisit(second.getId(), LocalDate.of(2026, 10, 13));
        var summary = service.getVisitSummary();
        assertThat(visits.count()).isEqualTo(3);
        assertThat(summary.getTodayVisitors()).isEqualTo(2);
        assertThat(summary.getYesterdayVisitors()).isEqualTo(1);
        assertThat(summary.getDaily().subList(0, 5)).allMatch(day -> day.getVisitors() == 0);
    }

    @Test void accountDeletionRemovesVisitsAndCannotRecreateDeletedUserHistory() {
        service.createVisit(first.getId(), LocalDate.of(2026, 10, 13));
        service.createVisit(second.getId(), LocalDate.of(2026, 10, 13));
        userService.deleteMe(first.getId());
        assertThat(users.findById(first.getId())).isEmpty();
        assertThat(visits.count()).isEqualTo(1);
        assertThat(service.getVisitSummary().getTodayVisitors()).isEqualTo(1);
        assertThatThrownBy(() -> service.createVisit(first.getId(), LocalDate.of(2026, 10, 13)))
                .isInstanceOf(com.myyak.apiPayload.exception.GeneralException.class);
        assertThat(visits.count()).isEqualTo(1);
    }

    @Test void activityAfterCommitCanSeeANewAccountAndRollbackDoesNotRecordIt() {
        var transaction = new TransactionTemplate(transactions);
        Long id = transaction.execute(status -> {
            User created = users.save(User.builder().kakaoId("test_visit_committed").name("visit-committed").build());
            tracking.recordVisitAfterCommit(created.getId());
            assertThat(visits.count()).isZero();
            return created.getId();
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.existsByUserIdAndVisitDate(id, LocalDate.of(2026, 10, 13))).isTrue());
        transaction.executeWithoutResult(status -> {
            User created = users.save(User.builder().kakaoId("test_visit_rolled_back").name("visit-rollback").build());
            tracking.recordVisitAfterCommit(created.getId());
            status.setRollbackOnly();
        });
        assertThat(visits.count()).isEqualTo(1);
        assertThat(users.findByKakaoId("test_visit_rolled_back")).isEmpty();
    }

    @TestConfiguration static class Services {
        @Bean MutableClock visitClock() { return new MutableClock(new AtomicReference<>(), ZoneOffset.UTC); }
        @Bean VisitService visitService(UserRepository users, UserVisitRepository visits, MutableClock clock) {
            return new VisitServiceImpl(users, visits, clock);
        }
        @Bean VisitTrackingService visitTrackingService(VisitService visits, MutableClock clock,
                @Qualifier("visitStatisticsExecutor") Executor executor) {
            return new VisitTrackingServiceImpl(visits, clock, executor);
        }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> at;
        final ZoneId zone;
        MutableClock(AtomicReference<Instant> at, ZoneId zone) { this.at = at; this.zone = zone; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId next) { return new MutableClock(at, next); }
        public Instant instant() { return at.get(); }
    }
}
