package com.myyak.service.adminService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SignupPurpose;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.SupplementRepository;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserSupplementRepository;
import com.myyak.service.llm.LlmClient;
import com.myyak.service.storage.StorageClient;
import com.myyak.web.dto.AdminDTO.AdminRequestDTO;
import com.myyak.web.dto.AdminDTO.AdminResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final DrugInfoRepository drugInfoRepository;
    private final SupplementRepository supplementRepository;
    private final UserRepository userRepository;
    private final UserSupplementRepository userSupplementRepository;
    private final DataSource dataSource;
    private final StorageClient storageClient;
    private final LlmClient llmClient;

    // 서버 로그 형식: 2026-01-08T04:17:25.346Z  INFO 16927 --- [myyak-server] [           main] c.m.Class : Message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z?)\\s+" +
            "(\\w+)\\s+" +
            "\\d+\\s+---\\s+" +
            "\\[[^\\]]+\\]\\s+" +  // [myyak-server] 부분
            "\\[([^\\]]+)\\]\\s+" +  // [main] 스레드명
            "([^:]+)\\s*:\\s*(.*)");

    private static final DateTimeFormatter LOG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @Value("${app.build-time:unknown}")
    private String buildTime;

    @Override
    public AdminResponseDTO.DrugStats getDrugStats() {
        long totalCount = drugInfoRepository.count();
        long withoutEfficacy = drugInfoRepository.countByEfficacyIsNullOrEmpty();
        long withoutIngredient = drugInfoRepository.countByIngredientKrIsNull();

        return AdminResponseDTO.DrugStats.builder()
                .totalCount(totalCount)
                .withoutEfficacy(withoutEfficacy)
                .withoutIngredient(withoutIngredient)
                .build();
    }

    @Override
    public AdminResponseDTO.SupplementList getRecentSupplements(int page, int size, Integer days, String search) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Supplement> supplementPage;

        if (search != null && !search.isBlank()) {
            supplementPage = supplementRepository.searchByKeyword(search.trim(), pageRequest);
        } else {
            supplementPage = supplementRepository.findAll(pageRequest);
        }

        // days 필터링 (메모리에서 처리 - 단순화를 위해)
        LocalDateTime cutoffDate = days != null ? LocalDateTime.now().minusDays(days) : null;

        List<AdminResponseDTO.SupplementItem> items = supplementPage.getContent().stream()
                .filter(s -> cutoffDate == null || s.getCreatedAt().isAfter(cutoffDate))
                .map(this::toSupplementItem)
                .collect(Collectors.toList());

        return AdminResponseDTO.SupplementList.builder()
                .supplements(items)
                .page(page)
                .size(size)
                .totalPages(supplementPage.getTotalPages())
                .totalElements(supplementPage.getTotalElements())
                .build();
    }

    @Override
    public AdminResponseDTO.SupplementTagStats getSupplementTagStats() {
        List<Supplement> allSupplements = supplementRepository.findAll();

        Map<SupplementTag, Long> tagCounts = allSupplements.stream()
                .collect(Collectors.groupingBy(Supplement::getTag, Collectors.counting()));

        return AdminResponseDTO.SupplementTagStats.builder()
                .tagCounts(tagCounts)
                .totalCount(allSupplements.size())
                .build();
    }

    @Override
    public AdminResponseDTO.UserStats getUserStats() {
        var allUsers = userRepository.findAll();
        long total = allUsers.size();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        long today = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(todayStart))
                .count();

        long week = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(weekStart))
                .count();

        long month = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(monthStart))
                .count();

        // 성별 분포
        Map<String, Long> byGender = new LinkedHashMap<>();
        byGender.put("MALE", allUsers.stream().filter(u -> u.getGender() == Gender.MALE).count());
        byGender.put("FEMALE", allUsers.stream().filter(u -> u.getGender() == Gender.FEMALE).count());
        byGender.put("UNKNOWN", allUsers.stream().filter(u -> u.getGender() == null).count());

        // 연령대 분포 (ageRange 문자열 기반)
        Map<String, Long> byAgeGroup = new LinkedHashMap<>();
        byAgeGroup.put("10s", countByAgeRange(allUsers, "10대"));
        byAgeGroup.put("20s", countByAgeRange(allUsers, "20대"));
        byAgeGroup.put("30s", countByAgeRange(allUsers, "30대"));
        byAgeGroup.put("40s", countByAgeRange(allUsers, "40대"));
        byAgeGroup.put("50s", countByAgeRange(allUsers, "50대"));
        byAgeGroup.put("60+", countByAgeRange(allUsers, "60대 이상"));
        byAgeGroup.put("UNKNOWN", allUsers.stream().filter(u -> u.getAgeRange() == null).count());

        // 가입목적 분포
        Map<String, Long> bySignupPurpose = new LinkedHashMap<>();
        for (SignupPurpose purpose : SignupPurpose.values()) {
            long count = allUsers.stream()
                    .filter(u -> u.getSignupPurposes() != null && u.getSignupPurposes().contains(purpose.name()))
                    .count();
            bySignupPurpose.put(purpose.name(), count);
        }

        return AdminResponseDTO.UserStats.builder()
                .total(total)
                .today(today)
                .week(week)
                .month(month)
                .byGender(byGender)
                .byAgeGroup(byAgeGroup)
                .bySignupPurpose(bySignupPurpose)
                .build();
    }

    @Override
    public AdminResponseDTO.DailySignups getDailySignups(int days) {
        var allUsers = userRepository.findAll();
        LocalDate today = LocalDate.now();

        List<AdminResponseDTO.DailyCount> dailyCounts = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            long count = allUsers.stream()
                    .filter(u -> u.getCreatedAt() != null &&
                            u.getCreatedAt().isAfter(startOfDay) &&
                            u.getCreatedAt().isBefore(endOfDay))
                    .count();

            dailyCounts.add(AdminResponseDTO.DailyCount.builder()
                    .date(date)
                    .count(count)
                    .build());
        }

        return AdminResponseDTO.DailySignups.builder()
                .dailyCounts(dailyCounts)
                .build();
    }

    @Override
    @Transactional
    public AdminResponseDTO.SupplementDeleteResult deleteSupplement(Long supplementId) {
        Supplement supplement = supplementRepository.findById(supplementId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));

        // 해당 영양제를 선택한 UserSupplement 수 조회
        var userSupplements = userSupplementRepository.findAll().stream()
                .filter(us -> us.getSupplement().getId().equals(supplementId))
                .collect(Collectors.toList());

        int deletedUserSupplementCount = userSupplements.size();

        // UserSupplement 삭제
        userSupplementRepository.deleteAll(userSupplements);

        // Supplement 삭제
        supplementRepository.delete(supplement);

        log.info("영양제 삭제 완료: id={}, 삭제된 UserSupplement 수={}", supplementId, deletedUserSupplementCount);

        return AdminResponseDTO.SupplementDeleteResult.builder()
                .deletedSupplementId(supplementId)
                .deletedUserSupplementCount(deletedUserSupplementCount)
                .build();
    }

    private AdminResponseDTO.SupplementItem toSupplementItem(Supplement supplement) {
        return AdminResponseDTO.SupplementItem.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .tag(supplement.getTag())
                .tagDescription(supplement.getTag().getDescription())
                .selectionCount(supplement.getSelectionCount())
                .createdAt(supplement.getCreatedAt())
                .createdByName(supplement.getCreatedBy() != null ? supplement.getCreatedBy().getName() : "알 수 없음")
                .build();
    }

    private long countByAgeRange(List<User> users, String ageRange) {
        return users.stream()
                .filter(u -> ageRange.equals(u.getAgeRange()))
                .count();
    }

    @Override
    public AdminResponseDTO.HealthStatus checkHealth() {
        long startTime = System.currentTimeMillis();

        // DB 연결 확인
        boolean databaseUp = checkDatabaseHealth();

        // 스토리지 연결 확인
        boolean storageUp = storageClient.isHealthy();

        // JVM 메모리 정보
        Runtime runtime = Runtime.getRuntime();
        long heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long heapMaxMb = runtime.maxMemory() / (1024 * 1024);

        // CPU 사용량
        double cpuUsage = getCpuUsage();

        long responseTimeMs = System.currentTimeMillis() - startTime;

        return AdminResponseDTO.HealthStatus.builder()
                .serverUp(true)
                .databaseUp(databaseUp)
                .storageUp(storageUp)
                .responseTimeMs(responseTimeMs)
                .storageProvider(storageClient.getProviderName())
                .heapUsedMb(heapUsedMb)
                .heapMaxMb(heapMaxMb)
                .cpuUsage(cpuUsage)
                .appVersion(appVersion)
                .buildTime(buildTime)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private boolean checkDatabaseHealth() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.warn("DB 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }

    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                return Math.round(sunOsBean.getCpuLoad() * 1000.0) / 10.0;
            }
        } catch (Exception e) {
            log.warn("CPU 사용량 조회 실패: {}", e.getMessage());
        }
        return -1;
    }

    @Override
    public AdminResponseDTO.ErrorLogList getErrorLogs(int page, int size, String level, Integer hours) {
        List<RawLogEntry> rawLogs = new ArrayList<>();

        try {
            String sinceTime = hours != null ? hours + "h ago" : "24h ago";
            ProcessBuilder pb = new ProcessBuilder(
                    "journalctl",
                    "-u", "myyak",
                    "--since", sinceTime,
                    "--no-pager",
                    "-o", "cat"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                StringBuilder currentStackTrace = new StringBuilder();
                RawLogEntry currentEntry = null;

                while ((line = reader.readLine()) != null) {
                    Matcher matcher = LOG_PATTERN.matcher(line);

                    if (matcher.matches()) {
                        if (currentEntry != null) {
                            if (currentStackTrace.length() > 0) {
                                currentEntry.stackTrace = currentStackTrace.toString();
                            }
                            rawLogs.add(currentEntry);
                        }

                        String timestamp = matcher.group(1);
                        String logLevel = matcher.group(2);
                        String threadName = matcher.group(3).trim();
                        String logger = matcher.group(4).trim();
                        String message = matcher.group(5);

                        LocalDateTime parsedTime;
                        try {
                            parsedTime = LocalDateTime.parse(timestamp, LOG_DATE_FORMATTER);
                        } catch (Exception e) {
                            parsedTime = LocalDateTime.now();
                        }

                        currentEntry = new RawLogEntry();
                        currentEntry.timestamp = parsedTime;
                        currentEntry.level = logLevel;
                        currentEntry.logger = logger;
                        currentEntry.message = message;
                        currentEntry.threadName = threadName;
                        currentStackTrace = new StringBuilder();

                    } else if (currentEntry != null && (line.startsWith("\t") || line.startsWith("    ") || line.contains("at "))) {
                        if (currentStackTrace.length() > 0) {
                            currentStackTrace.append("\n");
                        }
                        currentStackTrace.append(line);
                    }
                }

                if (currentEntry != null) {
                    if (currentStackTrace.length() > 0) {
                        currentEntry.stackTrace = currentStackTrace.toString();
                    }
                    rawLogs.add(currentEntry);
                }
            }

            process.waitFor();

        } catch (Exception e) {
            log.error("에러 로그 조회 실패: {}", e.getMessage(), e);
        }

        // 1. 이벤트 그룹화: 같은 스레드에서 1초 이내 발생한 로그를 묶음
        List<LogEventGroup> eventGroups = groupLogsByEvent(rawLogs);

        // 2. 레벨 필터링 (이벤트 내 대표 레벨 기준)
        if (level != null && !level.isBlank()) {
            eventGroups = eventGroups.stream()
                    .filter(g -> level.equalsIgnoreCase(g.representativeLevel))
                    .collect(Collectors.toList());
        }

        // 3. 중복 제거 로직: 동일 이벤트는 3번까지 개별 표시, 그 이상은 카운트
        List<AdminResponseDTO.ErrorLogItem> deduplicatedLogs = deduplicateEventGroups(eventGroups);

        // 4. 최신순 정렬
        deduplicatedLogs.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        long totalElements = deduplicatedLogs.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, deduplicatedLogs.size());

        List<AdminResponseDTO.ErrorLogItem> pagedLogs = fromIndex < deduplicatedLogs.size()
                ? deduplicatedLogs.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return AdminResponseDTO.ErrorLogList.builder()
                .logs(pagedLogs)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    /**
     * 로그를 이벤트 단위로 그룹화
     * 같은 스레드에서 1초 이내에 발생한 로그들을 하나의 이벤트로 묶음
     */
    private List<LogEventGroup> groupLogsByEvent(List<RawLogEntry> rawLogs) {
        List<LogEventGroup> groups = new ArrayList<>();
        if (rawLogs.isEmpty()) return groups;

        LogEventGroup currentGroup = null;

        for (RawLogEntry entry : rawLogs) {
            if (currentGroup == null) {
                currentGroup = new LogEventGroup(entry);
            } else if (currentGroup.shouldInclude(entry)) {
                currentGroup.addEntry(entry);
            } else {
                groups.add(currentGroup);
                currentGroup = new LogEventGroup(entry);
            }
        }

        if (currentGroup != null) {
            groups.add(currentGroup);
        }

        return groups;
    }

    /**
     * 중복 이벤트 그룹 제거: 동일한 이벤트는 3번까지 개별 표시, 그 이상은 카운트로 표시
     */
    private List<AdminResponseDTO.ErrorLogItem> deduplicateEventGroups(List<LogEventGroup> eventGroups) {
        List<AdminResponseDTO.ErrorLogItem> result = new ArrayList<>();
        Map<String, List<LogEventGroup>> signatureToGroups = new LinkedHashMap<>();

        // 동일 이벤트 시그니처로 그룹화 (대표 레벨 + 대표 로거 + 대표 메시지)
        for (LogEventGroup group : eventGroups) {
            String signature = group.getSignature();
            signatureToGroups.computeIfAbsent(signature, k -> new ArrayList<>()).add(group);
        }

        // 각 시그니처별로 처리
        for (Map.Entry<String, List<LogEventGroup>> entry : signatureToGroups.entrySet()) {
            List<LogEventGroup> groups = entry.getValue();
            int totalCount = groups.size();

            if (totalCount <= 3) {
                // 3번 이하면 개별 표시
                for (LogEventGroup group : groups) {
                    result.add(toErrorLogItem(group, 1));
                }
            } else {
                // 3번 초과면 최신 1개만 표시하고 총 카운트 표기
                LogEventGroup latestGroup = groups.get(groups.size() - 1);
                result.add(toErrorLogItem(latestGroup, totalCount));
            }
        }

        return result;
    }

    private AdminResponseDTO.ErrorLogItem toErrorLogItem(LogEventGroup group, int occurrenceCount) {
        List<AdminResponseDTO.RelatedLog> relatedLogs = group.entries.stream()
                .skip(1)  // 첫 번째는 대표 로그이므로 제외
                .map(e -> AdminResponseDTO.RelatedLog.builder()
                        .timestamp(e.timestamp)
                        .level(e.level)
                        .logger(e.logger)
                        .message(e.message)
                        .stackTrace(e.stackTrace)
                        .build())
                .collect(Collectors.toList());

        RawLogEntry representative = group.getRepresentativeEntry();

        return AdminResponseDTO.ErrorLogItem.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(group.startTime)
                .level(group.representativeLevel)
                .logger(representative.logger)
                .message(representative.message)
                .stackTrace(representative.stackTrace)
                .threadName(group.threadName)
                .occurrenceCount(occurrenceCount)
                .relatedLogCount(group.entries.size())
                .relatedLogs(relatedLogs.isEmpty() ? null : relatedLogs)
                .build();
    }

    /**
     * 로그 파싱용 내부 클래스
     */
    private static class RawLogEntry {
        LocalDateTime timestamp;
        String level;
        String logger;
        String message;
        String stackTrace;
        String threadName;
    }

    /**
     * 이벤트 그룹 클래스
     * 같은 스레드에서 1초 이내에 발생한 로그들의 그룹
     */
    private static class LogEventGroup {
        private static final long EVENT_WINDOW_SECONDS = 1;
        private static final Map<String, Integer> LEVEL_PRIORITY = Map.of(
                "ERROR", 4, "WARN", 3, "INFO", 2, "DEBUG", 1, "TRACE", 0
        );

        String threadName;
        LocalDateTime startTime;
        LocalDateTime lastTime;
        String representativeLevel;
        List<RawLogEntry> entries = new ArrayList<>();

        LogEventGroup(RawLogEntry firstEntry) {
            this.threadName = firstEntry.threadName;
            this.startTime = firstEntry.timestamp;
            this.lastTime = firstEntry.timestamp;
            this.representativeLevel = firstEntry.level;
            this.entries.add(firstEntry);
        }

        boolean shouldInclude(RawLogEntry entry) {
            // 같은 스레드이고, 마지막 로그로부터 1초 이내면 같은 이벤트
            if (!threadName.equals(entry.threadName)) return false;

            long secondsDiff = java.time.Duration.between(lastTime, entry.timestamp).getSeconds();
            return secondsDiff <= EVENT_WINDOW_SECONDS;
        }

        void addEntry(RawLogEntry entry) {
            entries.add(entry);
            lastTime = entry.timestamp;

            // 더 심각한 레벨로 업데이트
            int currentPriority = LEVEL_PRIORITY.getOrDefault(representativeLevel, 0);
            int newPriority = LEVEL_PRIORITY.getOrDefault(entry.level, 0);
            if (newPriority > currentPriority) {
                representativeLevel = entry.level;
            }
        }

        RawLogEntry getRepresentativeEntry() {
            // ERROR 레벨 로그가 있으면 그것을 대표로, 없으면 첫 번째
            return entries.stream()
                    .filter(e -> "ERROR".equals(e.level))
                    .findFirst()
                    .orElse(entries.get(0));
        }

        String getSignature() {
            RawLogEntry rep = getRepresentativeEntry();
            return representativeLevel + "|" + rep.logger + "|" + rep.message;
        }
    }

    @Override
    public AdminResponseDTO.ChatResponse chat(AdminRequestDTO.ChatRequest request) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are an expert Java/Spring Boot developer helping to analyze server error logs. ");
        systemPrompt.append("Analyze the error and provide practical solutions in Korean. ");
        systemPrompt.append("Be concise but thorough.\n\n");
        systemPrompt.append("Error Log Context:\n");

        AdminRequestDTO.ErrorLogContext errorLog = request.getErrorLog();
        if (errorLog != null) {
            systemPrompt.append("- Timestamp: ").append(errorLog.getTimestamp()).append("\n");
            systemPrompt.append("- Level: ").append(errorLog.getLevel()).append("\n");
            systemPrompt.append("- Logger: ").append(errorLog.getLogger()).append("\n");
            systemPrompt.append("- Message: ").append(errorLog.getMessage()).append("\n");
            if (errorLog.getStackTrace() != null) {
                systemPrompt.append("- Stack Trace:\n").append(errorLog.getStackTrace()).append("\n");
            }
        }

        StringBuilder conversationContext = new StringBuilder();
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            conversationContext.append("\n\nPrevious conversation:\n");
            for (AdminRequestDTO.ChatMessage msg : request.getMessages()) {
                conversationContext.append(msg.getRole().toUpperCase()).append(": ");
                conversationContext.append(msg.getContent()).append("\n\n");
            }
        }

        String userPrompt = conversationContext.toString() + "\nUSER: " + request.getUserMessage();

        try {
            String response = llmClient.generate(systemPrompt.toString(), userPrompt);

            return AdminResponseDTO.ChatResponse.builder()
                    .response(response)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("AI 채팅 오류: {}", e.getMessage(), e);
            return AdminResponseDTO.ChatResponse.builder()
                    .response("AI 응답 생성 중 오류가 발생했습니다: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
}
