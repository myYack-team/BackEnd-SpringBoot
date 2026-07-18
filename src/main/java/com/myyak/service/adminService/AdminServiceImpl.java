package com.myyak.service.adminService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.converter.AdminConverter;
import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SignupPurpose;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.domain.AppSetting;
import com.myyak.repository.AppSettingRepository;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.SupplementRepository;
import com.myyak.repository.UserMedicationRepository;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserSupplementRepository;
import com.myyak.service.llm.LlmClient;
import com.myyak.service.storage.StorageClient;
import com.myyak.service.userService.UserService;
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
import java.time.OffsetDateTime;
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
    private final UserService userService;
    private final UserMedicationRepository userMedicationRepository;
    private final AppSettingRepository appSettingRepository;

    // 서버 로그 형식: 2026-01-09T11:44:03.382+09:00  INFO 31804 --- [myyak-server] [           main] c.m.Class : Message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+[^\\s]*)\\s+" +
            "(\\w+)\\s+" +
            "\\d+\\s+---\\s+" +
            "\\[[^\\]]+\\]\\s+" +  // [myyak-server] 부분
            "\\[([^\\]]+)\\]\\s+" +  // [main] 스레드명
            "([^:]+)\\s*:\\s*(.*)");

    private static final DateTimeFormatter LOG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

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

        // days 필터링 (쿼리 조건으로 처리)
        LocalDateTime cutoffDate = days != null ? LocalDateTime.now().minusDays(days) : null;

        Page<Supplement> supplementPage;

        if (search != null && !search.isBlank()) {
            String keyword = search.trim();
            supplementPage = cutoffDate != null
                    ? supplementRepository.searchByKeywordAndCreatedAtAfter(keyword, cutoffDate, pageRequest)
                    : supplementRepository.searchByKeyword(keyword, pageRequest);
        } else {
            supplementPage = cutoffDate != null
                    ? supplementRepository.findByCreatedAtAfter(cutoffDate, pageRequest)
                    : supplementRepository.findAll(pageRequest);
        }

        return AdminConverter.toSupplementList(supplementPage, page, size);
    }

    @Override
    public AdminResponseDTO.SupplementTagStats getSupplementTagStats() {
        // 태그별 개수 DB 집계
        Map<SupplementTag, Long> tagCounts = new LinkedHashMap<>();
        long totalCount = 0;

        for (Object[] row : supplementRepository.countGroupByTag()) {
            SupplementTag tag = (SupplementTag) row[0];
            long count = (Long) row[1];
            tagCounts.put(tag, count);
            totalCount += count;
        }

        return AdminConverter.toSupplementTagStats(tagCounts, totalCount);
    }

    @Override
    public AdminResponseDTO.UserStats getUserStats() {
        long total = userRepository.count();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        long today = userRepository.countByCreatedAtAfter(todayStart);
        long week = userRepository.countByCreatedAtAfter(weekStart);
        long month = userRepository.countByCreatedAtAfter(monthStart);

        // 성별 분포 (DB 집계)
        Map<String, Long> byGender = new LinkedHashMap<>();
        byGender.put("MALE", 0L);
        byGender.put("FEMALE", 0L);
        byGender.put("UNKNOWN", 0L);
        for (Object[] row : userRepository.countGroupByGender()) {
            Gender gender = (Gender) row[0];
            long count = (Long) row[1];
            byGender.put(gender != null ? gender.name() : "UNKNOWN", count);
        }

        // 연령대 분포 (DB 집계, ageRange 문자열 기반)
        Map<String, Long> ageRangeCounts = new HashMap<>();
        long unknownAgeCount = 0;
        for (Object[] row : userRepository.countGroupByAgeRange()) {
            String ageRange = (String) row[0];
            long count = (Long) row[1];
            if (ageRange == null) {
                unknownAgeCount += count;
            } else {
                ageRangeCounts.put(ageRange, count);
            }
        }

        Map<String, Long> byAgeGroup = new LinkedHashMap<>();
        byAgeGroup.put("10s", ageRangeCounts.getOrDefault("10대", 0L));
        byAgeGroup.put("20s", ageRangeCounts.getOrDefault("20대", 0L));
        byAgeGroup.put("30s", ageRangeCounts.getOrDefault("30대", 0L));
        byAgeGroup.put("40s", ageRangeCounts.getOrDefault("40대", 0L));
        byAgeGroup.put("50s", ageRangeCounts.getOrDefault("50대", 0L));
        byAgeGroup.put("60+", ageRangeCounts.getOrDefault("60대 이상", 0L));
        byAgeGroup.put("UNKNOWN", unknownAgeCount);

        // 가입목적 분포 (콤마 구분 문자열이라 해당 컬럼만 조회 후 메모리 집계)
        List<String> allSignupPurposes = userRepository.findAllSignupPurposes();
        Map<String, Long> bySignupPurpose = new LinkedHashMap<>();
        for (SignupPurpose purpose : SignupPurpose.values()) {
            long count = allSignupPurposes.stream()
                    .filter(purposes -> purposes.contains(purpose.name()))
                    .count();
            bySignupPurpose.put(purpose.name(), count);
        }

        return AdminConverter.toUserStats(total, today, week, month, byGender, byAgeGroup, bySignupPurpose);
    }

    @Override
    public AdminResponseDTO.DailySignups getDailySignups(int days) {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(days - 1).atStartOfDay();

        // 일자별 가입자 수 DB 집계 (GROUP BY DATE)
        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : userRepository.countDailySignups(since)) {
            countsByDate.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }

        List<AdminResponseDTO.DailyCount> dailyCounts = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            dailyCounts.add(AdminConverter.toDailyCount(date, countsByDate.getOrDefault(date, 0L)));
        }

        return AdminConverter.toDailySignups(dailyCounts);
    }

    /**
     * Native Query의 DATE() 결과를 LocalDate로 변환 (드라이버별 반환 타입 대응)
     */
    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }

    @Override
    @Transactional
    public AdminResponseDTO.SupplementDeleteResult deleteSupplement(Long supplementId) {
        Supplement supplement = supplementRepository.findById(supplementId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));

        // 해당 영양제를 선택한 UserSupplement 일괄 삭제 (삭제된 수 반환)
        int deletedUserSupplementCount = userSupplementRepository.deleteBySupplement(supplement);

        // Supplement 삭제
        supplementRepository.delete(supplement);

        log.info("영양제 삭제 완료: id={}, 삭제된 UserSupplement 수={}", supplementId, deletedUserSupplementCount);

        return AdminConverter.toSupplementDeleteResult(supplementId, deletedUserSupplementCount);
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
                            OffsetDateTime odt = OffsetDateTime.parse(timestamp, LOG_DATE_FORMATTER);
                            parsedTime = odt.toLocalDateTime();
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
                    .response("AI 응답 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public AdminResponseDTO.UserList getUserList(int page, int size, String search) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage;
        if (search != null && !search.isBlank()) {
            String trimmedSearch = search.trim();
            userPage = userRepository.searchByKeyword(trimmedSearch, pageRequest);
        } else {
            userPage = userRepository.findAll(pageRequest);
        }

        // 페이지 내 사용자들의 약물/영양제 수를 집계 쿼리 2회로 한 번에 조회 (N+1 방지)
        List<Long> userIds = userPage.getContent().stream()
                .map(User::getId)
                .collect(Collectors.toList());

        Map<Long, Long> medicationCounts = userIds.isEmpty()
                ? Collections.emptyMap()
                : toCountMap(userMedicationRepository.countByUserIds(userIds));
        Map<Long, Long> supplementCounts = userIds.isEmpty()
                ? Collections.emptyMap()
                : toCountMap(userSupplementRepository.countByUserIds(userIds));

        return AdminConverter.toUserList(userPage, medicationCounts, supplementCounts, page, size);
    }

    /**
     * (userId, count) 집계 결과를 Map으로 변환
     */
    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : rows) {
            countMap.put((Long) row[0], (Long) row[1]);
        }
        return countMap;
    }

    @Override
    @Transactional
    public AdminResponseDTO.BatchDeleteUsersResult batchDeleteUsers(AdminRequestDTO.BatchDeleteUsersRequest request) {
        List<Long> userIds = request.getUserIds();
        log.info("관리자 사용자 일괄 탈퇴 요청: {} 명", userIds.size());

        List<Long> deletedUserIds = new ArrayList<>();
        List<Long> failedUserIds = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                userService.deleteMe(userId);
                deletedUserIds.add(userId);
                log.info("사용자 탈퇴 완료: userId={}", userId);
            } catch (Exception e) {
                log.error("사용자 탈퇴 실패: userId={}, error={}", userId, e.getMessage());
                failedUserIds.add(userId);
            }
        }

        log.info("일괄 탈퇴 완료: 요청={}, 성공={}, 실패={}",
                userIds.size(), deletedUserIds.size(), failedUserIds.size());

        return AdminConverter.toBatchDeleteUsersResult(userIds.size(), deletedUserIds, failedUserIds);
    }

    // ===== AI 모델 설정 관련 =====

    @Value("${ai.gemini.analysis-model:gemini-3.1-pro-preview}")
    private String configAnalysisModel;

    // 2026-07-17 실호출 검증 기준 사용 가능 모델 (2.5 계열은 2026-10-16 지원 종료 예정)
    private static final List<String> AVAILABLE_MODELS = List.of(
            "gemini-3.1-pro-preview",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
    );

    @Override
    public AdminResponseDTO.AiModelSetting getAiModelSetting() {
        // DB에서 설정 조회, 없으면 config 기본값 사용
        String analysisModel = appSettingRepository.findBySettingKey(AppSetting.KEY_GEMINI_ANALYSIS_MODEL)
                .map(AppSetting::getSettingValue)
                .orElse(configAnalysisModel);

        String fallbackModel = appSettingRepository.findBySettingKey(AppSetting.KEY_GEMINI_ANALYSIS_FALLBACK_MODEL)
                .map(AppSetting::getSettingValue)
                .orElse("gemini-3.5-flash");

        boolean fallbackEnabled = appSettingRepository.findBySettingKey(AppSetting.KEY_GEMINI_FALLBACK_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(true);

        LocalDateTime updatedAt = appSettingRepository.findBySettingKey(AppSetting.KEY_GEMINI_ANALYSIS_MODEL)
                .map(AppSetting::getUpdatedAt)
                .orElse(null);

        return AdminResponseDTO.AiModelSetting.builder()
                .analysisModel(analysisModel)
                .fallbackModel(fallbackModel)
                .fallbackEnabled(fallbackEnabled)
                .configAnalysisModel(configAnalysisModel)
                .availableModels(AVAILABLE_MODELS)
                .updatedAt(updatedAt)
                .build();
    }

    @Override
    @Transactional
    public AdminResponseDTO.AiModelSetting updateAiModelSetting(AdminRequestDTO.AiModelSettingRequest request) {
        log.info("AI 모델 설정 변경 요청: model={}, fallback={}, enabled={}",
                request.getAnalysisModel(), request.getFallbackModel(), request.getFallbackEnabled());

        // 분석 모델 설정
        if (request.getAnalysisModel() != null) {
            updateOrCreateSetting(
                    AppSetting.KEY_GEMINI_ANALYSIS_MODEL,
                    request.getAnalysisModel(),
                    "Gemini 분석용 모델"
            );
        }

        // 폴백 모델 설정
        if (request.getFallbackModel() != null) {
            updateOrCreateSetting(
                    AppSetting.KEY_GEMINI_ANALYSIS_FALLBACK_MODEL,
                    request.getFallbackModel(),
                    "Gemini 폴백 모델"
            );
        }

        // 폴백 활성화 설정
        if (request.getFallbackEnabled() != null) {
            updateOrCreateSetting(
                    AppSetting.KEY_GEMINI_FALLBACK_ENABLED,
                    String.valueOf(request.getFallbackEnabled()),
                    "Gemini 폴백 활성화 여부"
            );
        }

        log.info("AI 모델 설정 변경 완료");

        return getAiModelSetting();
    }

    // ===== 테스트 로그인 관리 =====

    @Override
    public AdminResponseDTO.TestLoginStatus getTestLoginStatus() {
        return appSettingRepository.findBySettingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)
                .map(setting -> AdminResponseDTO.TestLoginStatus.builder()
                        .enabled(Boolean.parseBoolean(setting.getSettingValue()))
                        .updatedAt(setting.getUpdatedAt())
                        .build())
                .orElse(AdminResponseDTO.TestLoginStatus.builder()
                        .enabled(false)
                        .updatedAt(null)
                        .build());
    }

    @Override
    @Transactional
    public AdminResponseDTO.TestLoginStatus toggleTestLogin() {
        AppSetting setting = appSettingRepository.findBySettingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)
                .orElse(AppSetting.builder()
                        .settingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)
                        .settingValue("false")
                        .description("테스트 로그인 활성화 여부 (Google Play Store 심사용)")
                        .build());

        boolean currentValue = Boolean.parseBoolean(setting.getSettingValue());
        boolean newValue = !currentValue;

        setting.updateValue(String.valueOf(newValue));
        appSettingRepository.save(setting);

        log.info("테스트 로그인 상태 변경: {} -> {}", currentValue, newValue);

        return AdminResponseDTO.TestLoginStatus.builder()
                .enabled(newValue)
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    private void updateOrCreateSetting(String key, String value, String description) {
        AppSetting setting = appSettingRepository.findBySettingKey(key)
                .orElse(AppSetting.builder()
                        .settingKey(key)
                        .settingValue(value)
                        .description(description)
                        .build());

        setting.updateValue(value);
        appSettingRepository.save(setting);
    }
}
