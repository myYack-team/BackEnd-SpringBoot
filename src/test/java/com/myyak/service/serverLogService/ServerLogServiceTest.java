package com.myyak.service.serverLogService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ServerLogServiceTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);

    private void gzip(String name, String text) throws Exception {
        try (var output = new GZIPOutputStream(Files.newOutputStream(directory.resolve(name)))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test void readsCurrentAndRelevantCompressedFilesWithoutHostCommands() throws Exception {
        Path file = directory.resolve("application.log");
        Files.writeString(file, "현재 한글 로그\r\n\tat com.myyak.Example.call(Example.java:1)\n");
        gzip("application.log.2026-11-14.0.gz", "어제 로그\n");
        gzip("application.log.2026-11-14.2.gz", "회전 두 번째\n");
        gzip("application.log.2026-11-14.10.gz", "회전 열 번째\n");
        gzip("application.log.2026-11-01.0.gz", "제외할 오래된 로그\n");
        Files.writeString(directory.resolve("unrelated.log"), "무관한 파일");
        List<String> lines = new ArrayList<>();
        new ServerLogServiceImpl(file, clock).readLines(24, lines::add);
        assertEquals(List.of("어제 로그", "회전 두 번째", "회전 열 번째", "현재 한글 로그", "\tat com.myyak.Example.call(Example.java:1)"), lines);
    }

    @Test void missingOrCorruptFilesAreUnavailableInsteadOfSuccessfulEmptyResults() throws Exception {
        Path file = directory.resolve("application.log");
        var service = new ServerLogServiceImpl(file, clock);
        assertEquals(ErrorStatus.ADMIN_LOG_UNAVAILABLE,
                assertThrows(GeneralException.class, () -> service.readLines(24, ignored -> {})).getCode());
        Files.writeString(file, "");
        Files.writeString(directory.resolve("application.log.2026-11-15.0.gz"), "not gzip");
        assertEquals(ErrorStatus.ADMIN_LOG_UNAVAILABLE,
                assertThrows(GeneralException.class, () -> service.readLines(24, ignored -> {})).getCode());
    }

    @Test void oversizedLineAndCompressedExpansionAreBounded() throws Exception {
        Path file = directory.resolve("application.log");
        Files.writeString(file, "x".repeat(65537));
        var service = new ServerLogServiceImpl(file, clock);
        assertEquals(ErrorStatus.ADMIN_LOG_RANGE_TOO_LARGE,
                assertThrows(GeneralException.class, () -> service.readLines(24, ignored -> {})).getCode());
        Files.writeString(file, "");
        gzip("application.log.2026-11-15.0.gz", ("x".repeat(1023) + "\n").repeat(16385));
        assertEquals(ErrorStatus.ADMIN_LOG_RANGE_TOO_LARGE,
                assertThrows(GeneralException.class, () -> service.readLines(24, ignored -> {})).getCode());
    }
}
