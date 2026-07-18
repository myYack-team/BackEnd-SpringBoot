package com.myyak.service.clientErrorLogService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ClientErrorLogServiceImpl implements ClientErrorLogService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logDirectory;

    public ClientErrorLogServiceImpl(@Value("${app.error-log.directory:logs/client-errors}") String logDir) {
        this.logDirectory = Paths.get(logDir);
        try {
            Files.createDirectories(this.logDirectory);
            log.info("클라이언트 에러 로그 디렉토리: {}", this.logDirectory.toAbsolutePath());
        } catch (IOException e) {
            log.error("로그 디렉토리 생성 실패: {}", e.getMessage());
        }
    }

    @Override
    public void saveErrorLog(String level, String message, String stackTrace, String screen,
                             String appVersion, String platform, String osVersion,
                             String deviceModel, Long userId, String additionalInfo) {
        try {
            String today = LocalDate.now().format(DATE_FORMATTER);
            Path logFile = logDirectory.resolve("client-error-" + today + ".log");

            StringBuilder logEntry = new StringBuilder();
            logEntry.append("\n========================================\n");
            logEntry.append("[").append(LocalDateTime.now().format(DATETIME_FORMATTER)).append("] ");
            logEntry.append("[").append(level).append("]\n");
            logEntry.append("Message: ").append(message).append("\n");

            if (screen != null) logEntry.append("Screen: ").append(screen).append("\n");
            if (userId != null) logEntry.append("User ID: ").append(userId).append("\n");
            if (platform != null) logEntry.append("Platform: ").append(platform).append("\n");
            if (osVersion != null) logEntry.append("OS Version: ").append(osVersion).append("\n");
            if (deviceModel != null) logEntry.append("Device: ").append(deviceModel).append("\n");
            if (appVersion != null) logEntry.append("App Version: ").append(appVersion).append("\n");
            if (stackTrace != null && !stackTrace.isBlank()) {
                logEntry.append("Stack Trace:\n").append(stackTrace).append("\n");
            }
            if (additionalInfo != null && !additionalInfo.isBlank()) {
                logEntry.append("Additional Info: ").append(additionalInfo).append("\n");
            }
            logEntry.append("========================================\n");

            Files.writeString(logFile, logEntry.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.debug("클라이언트 에러 로그 저장: {} - {}", level, message);
        } catch (IOException e) {
            log.error("에러 로그 파일 저장 실패: {}", e.getMessage());
        }
    }

    @Override
    public List<String> getErrorLogs(LocalDate date) {
        try {
            Path logFile = logDirectory.resolve("client-error-" + date.format(DATE_FORMATTER) + ".log");
            if (Files.exists(logFile)) {
                return Files.readAllLines(logFile);
            }
        } catch (IOException e) {
            log.error("에러 로그 파일 읽기 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getLogFileList(int days) {
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(logDirectory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("client-error-"))
                    .filter(p -> p.getFileName().toString().endsWith(".log"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Collections.reverseOrder())
                    .limit(days)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("로그 파일 목록 조회 실패: {}", e.getMessage());
        }
        return files;
    }

    @Override
    public String getLogFileContent(LocalDate date) {
        try {
            Path logFile = logDirectory.resolve("client-error-" + date.format(DATE_FORMATTER) + ".log");
            if (Files.exists(logFile)) {
                return Files.readString(logFile);
            }
            return "해당 날짜의 로그 파일이 없습니다.";
        } catch (IOException e) {
            log.error("에러 로그 파일 읽기 실패: {}", e.getMessage());
            return "로그 파일 읽기 실패: " + e.getMessage();
        }
    }
}
