package com.myyak.service.adminService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.service.serverLogService.ServerLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLogTest {
    @Mock ServerLogService source;
    @InjectMocks AdminServiceImpl service;

    private String line(OffsetDateTime time, String level, String message) {
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
                + " " + level + " 1 --- [myyak-server] [test] c.m.Example : " + message;
    }

    @Test void filtersByInstantAndPreservesStackCausesAndRelatedLogs() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        doAnswer(call -> {
            Consumer<String> consumer = call.getArgument(1);
            consumer.accept(line(now.minusDays(2), "ERROR", "old"));
            consumer.accept(line(now, "WARN", "before failure"));
            consumer.accept(line(now.plusNanos(100_000_000), "ERROR", "synthetic failure"));
            consumer.accept("java.lang.IllegalStateException: synthetic");
            consumer.accept("\tat com.myyak.Example.call(Example.java:1)");
            consumer.accept("Caused by: java.io.IOException: synthetic");
            return null;
        }).when(source).readLines(eq(24), any());
        var result = service.getErrorLogs(0, 20, "ERROR", 24);
        assertEquals(1, result.getTotalElements());
        var entry = result.getLogs().getFirst();
        assertEquals("synthetic failure", entry.getMessage());
        assertEquals(2, entry.getRelatedLogCount());
        assertTrue(entry.getStackTrace().contains("Caused by:"));
        assertTrue(entry.getStackTrace().contains("IllegalStateException"));
        assertEquals(now.withOffsetSameInstant(ZoneOffset.ofHours(9)).getHour(), entry.getTimestamp().getHour());
    }

    @Test void fileFailureIsPropagatedAndDoesNotBecomeEmptySuccess() {
        doThrow(new GeneralException(ErrorStatus.ADMIN_LOG_UNAVAILABLE)).when(source).readLines(anyInt(), any());
        assertEquals(ErrorStatus.ADMIN_LOG_UNAVAILABLE,
                assertThrows(GeneralException.class, () -> service.getErrorLogs(0, 20, null, null)).getCode());
    }

    @Test void rejectsInvalidRangesAndHandlesLargePageWithoutOverflow() {
        assertThrows(GeneralException.class, () -> service.getErrorLogs(-1, 20, null, 24));
        assertThrows(GeneralException.class, () -> service.getErrorLogs(0, 0, null, 24));
        assertThrows(GeneralException.class, () -> service.getErrorLogs(0, 20, null, 169));
        assertThrows(GeneralException.class, () -> service.getErrorLogs(0, 20, "INVALID", 24));
        verifyNoInteractions(source);
        assertTrue(service.getErrorLogs(Integer.MAX_VALUE, 100, null, 24).getLogs().isEmpty());
    }

    @Test void malformedTimestampDoesNotBecomeANewLog() {
        doAnswer(call -> {
            Consumer<String> consumer = call.getArgument(1);
            consumer.accept("2026-99-99T00:00:00.000+09:00 ERROR 1 --- [myyak-server] [test] c.m.Example : invalid date");
            return null;
        }).when(source).readLines(anyInt(), any());
        assertTrue(service.getErrorLogs(0, 20, null, 24).getLogs().isEmpty());
    }

    @Test void httpApiReportsUnavailableInsteadOf200WithEmptyLogs() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.myyak.web.controller.AdminController(service))
                .setControllerAdvice(new com.myyak.apiPayload.exception.ExceptionAdvice()).build();
        doThrow(new GeneralException(ErrorStatus.ADMIN_LOG_UNAVAILABLE)).when(source).readLines(anyInt(), any());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/logs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.isSuccess").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("LOG503"));
    }
}
