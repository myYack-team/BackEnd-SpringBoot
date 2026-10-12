package com.myyak.service.intakeService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.*;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.IntakeDTO.IntakeRequestDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION_TEST", matches = "true")
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.format_sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntakeServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntakeConcurrencyIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv().getOrDefault("MYSQL_TEST_URL", "jdbc:mysql://127.0.0.1:13306/myyak_incident_test?serverTimezone=Asia/Seoul");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/myyak_incident_test(?:\\?.*)?")) throw new IllegalArgumentException("Only the disposable loopback test database is permitted");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "test"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "fixture-password"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    @Autowired IntakeService service;
    @Autowired IntakeRepository intakes;
    @Autowired UserMedicationRepository medications;
    @Autowired UserRepository users;
    @MockitoBean UserService userService;
    User owner, other;
    UserMedication first, second, foreign;

    @BeforeEach void prepare() {
        owner = users.save(User.builder().kakaoId("test-owner").name("owner").build());
        other = users.save(User.builder().kakaoId("test-other").name("other").build());
        first = medication(owner, "first"); second = medication(owner, "second"); foreign = medication(other, "foreign");
        // A real consistent read before the locking query reproduces the original isolation boundary.
        when(userService.findById(anyLong())).thenAnswer(call -> users.findById(call.getArgument(0)).orElseThrow());
    }
    @AfterEach void clean() { intakes.deleteAll(); medications.deleteAll(); users.deleteAll(); }
    UserMedication medication(User user, String name) {
        return medications.save(UserMedication.builder().user(user).customDrugName(name).dosage("1")
                .frequency(1).durationDays(30).totalCount(30).remainingCount(30).startDate(LocalDate.of(2026, 9, 1)).build());
    }
    IntakeRequestDTO.CreateRequest request(List<Long> ids, LocalDateTime at) {
        var value = new IntakeRequestDTO.CreateRequest();
        ReflectionTestUtils.setField(value, "medicationIds", ids); ReflectionTestUtils.setField(value, "takenAt", at);
        ReflectionTestUtils.setField(value, "timing", MedicationTiming.MORNING); ReflectionTestUtils.setField(value, "status", IntakeStatus.TAKEN);
        return value;
    }
    @Test void concurrentReversedBatchesDeduplicateWithoutDeadlockOrDoubleDecrement() throws Exception {
        var executor = Executors.newFixedThreadPool(6); var start = new CountDownLatch(1); List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 6; index++) {
                List<Long> ids = index % 2 == 0 ? List.of(first.getId(), second.getId()) : List.of(second.getId(), first.getId());
                futures.add(executor.submit(() -> { start.await(); return service.createIntake(owner.getId(), request(ids, LocalDateTime.of(2026, 9, 10, 8, 0))); }));
            }
            start.countDown(); for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(2, intakes.count());
        assertEquals(29, medications.findById(first.getId()).orElseThrow().getRemainingCount());
        assertEquals(29, medications.findById(second.getId()).orElseThrow().getRemainingCount());
    }
    @Test void duplicateMissingAndForeignIdsFailAtomically() {
        for (List<Long> ids : List.of(List.of(first.getId(), first.getId()), List.of(first.getId(), Long.MAX_VALUE), List.of(first.getId(), foreign.getId()))) {
            assertThrows(GeneralException.class, () -> service.createIntake(owner.getId(), request(ids, LocalDateTime.of(2026, 9, 10, 8, 0))));
            assertEquals(0, intakes.count()); assertEquals(30, medications.findById(first.getId()).orElseThrow().getRemainingCount());
        }
    }
    @Test void adjacentKoreanDatesRemainSeparateAndPastCompletionUsesTakenDate() {
        service.createIntake(owner.getId(), request(List.of(first.getId()), LocalDateTime.of(2026, 9, 11, 0, 0)));
        service.createIntake(owner.getId(), request(List.of(first.getId()), LocalDateTime.of(2026, 9, 10, 23, 59, 59, 999000000)));
        assertEquals(2, intakes.count()); assertEquals(28, medications.findById(first.getId()).orElseThrow().getRemainingCount());
        second.setRemainingCount(1); medications.save(second);
        service.createIntake(owner.getId(), request(List.of(second.getId()), LocalDateTime.of(2026, 9, 9, 8, 0)));
        var completed = medications.findById(second.getId()).orElseThrow();
        assertFalse(completed.getIsActive()); assertEquals(LocalDate.of(2026, 9, 9), completed.getEndDate());
    }
}
