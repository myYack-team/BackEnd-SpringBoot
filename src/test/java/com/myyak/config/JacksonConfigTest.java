package com.myyak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigTest {

    private static TimeZone originalTimeZone;
    private static ObjectMapper objectMapper;

    record TakenAtPayload(LocalDateTime takenAt) {}

    @BeforeAll
    static void setUp() {
        // MyyakServerApplication과 동일하게 기본 시간대를 KST로 고정
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

        // 스프링 부트가 ObjectMapper를 만드는 경로(빌더 + 커스터마이저)를 그대로 재현
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().zoneAwareLocalDateTimeCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @AfterAll
    static void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    private LocalDateTime parse(String takenAt) throws Exception {
        return objectMapper.readValue("{\"takenAt\":\"" + takenAt + "\"}", TakenAtPayload.class).takenAt();
    }

    @Test
    void utcOffsetIsConvertedToKst() throws Exception {
        // 클라이언트 toISOString() 형식
        assertEquals(LocalDateTime.of(2026, 9, 10, 10, 26, 13, 599_000_000),
                parse("2026-09-10T01:26:13.599Z"));
    }

    @Test
    void dateBoundaryFollowsKst() throws Exception {
        // UTC 기준 전날 밤 → KST 기준 당일 아침으로 날짜가 넘어와야 함
        assertEquals(LocalDateTime.of(2026, 9, 10, 8, 30),
                parse("2026-09-09T23:30:00Z"));
    }

    @Test
    void explicitOffsetIsConverted() throws Exception {
        assertEquals(LocalDateTime.of(2026, 9, 10, 10, 26, 13),
                parse("2026-09-10T10:26:13+09:00"));
    }

    @Test
    void localDateTimeWithoutOffsetIsKeptAsIs() throws Exception {
        // 과거 날짜 기록 경로: 오프셋 없는 로컬 시각은 그대로
        assertEquals(LocalDateTime.of(2026, 9, 10, 10, 26, 13),
                parse("2026-09-10T10:26:13"));
    }
}
