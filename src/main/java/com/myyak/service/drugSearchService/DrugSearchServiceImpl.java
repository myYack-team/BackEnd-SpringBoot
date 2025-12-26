package com.myyak.service.drugSearchService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugInfoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 자모 기반 편집거리 검색 서비스 구현체
 *
 * 한글을 자모(초성/중성/종성) 단위로 분해하여 Levenshtein Distance를 계산합니다.
 * 예: "아젤론정" → "ㅇㅏㅈㅔㄹㅗㄴㅈㅓㅇ"
 *
 * OCR 오타에서 "이젤론정"으로 인식되었을 때 자모 기반 편집거리로
 * "아젤론정"을 정확히 찾아낼 수 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugSearchServiceImpl implements DrugSearchService {

    private final DrugInfoRepository drugInfoRepository;

    private static final int DEFAULT_THRESHOLD = 3;

    /**
     * 약물명 자모 분해 캐시
     * Key: itemSeq, Value: 자모 분해된 약물명
     */
    private final Map<String, String> jamoCache = new ConcurrentHashMap<>();

    /**
     * 약물명 원본 캐시
     * Key: itemSeq, Value: 원본 약물명 (괄호 제거)
     */
    private final Map<String, String> nameCache = new ConcurrentHashMap<>();

    /**
     * 전체 DrugInfo 캐시 (DB 쿼리 없이 검색용)
     * Key: itemSeq, Value: DrugInfo 객체
     */
    private final Map<String, DrugInfo> drugInfoCache = new ConcurrentHashMap<>();

    /**
     * 검색 결과 캐시 (동일 키워드 반복 검색 최적화)
     * Key: "keyword:page:size", Value: 검색 결과 리스트
     * TTL: 5분, 최대 1000개 엔트리
     */
    private final Cache<String, List<DrugInfo>> searchResultCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * 검색 결과 카운트 캐시
     * Key: keyword, Value: 매칭된 총 개수
     * TTL: 5분, 최대 500개 엔트리
     */
    private final Cache<String, Long> searchCountCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @PostConstruct
    public void init() {
        log.info("약물 캐시 초기화 시작...");
        long startTime = System.currentTimeMillis();

        List<DrugInfo> allDrugs = drugInfoRepository.findAll();
        for (DrugInfo drug : allDrugs) {
            String itemSeq = drug.getItemSeq();
            String itemName = drug.getItemName();
            if (itemName != null && !itemName.isBlank()) {
                // 괄호 제거한 순수 약물명으로 자모 분해
                String pureName = extractPureDrugName(itemName);
                jamoCache.put(itemSeq, decomposeToJamo(pureName));
                nameCache.put(itemSeq, pureName);
                // DrugInfo 전체 캐시 (DB 쿼리 없이 검색용)
                drugInfoCache.put(itemSeq, drug);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("약물 캐시 초기화 완료: {}개 약물, {}ms", drugInfoCache.size(), elapsed);
    }

    @Override
    public Optional<DrugInfo> findByNameContaining(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        // 공백 제거 (DB의 약물명은 공백 없이 저장됨)
        String normalizedKeyword = keyword.replaceAll("\\s+", "");

        // 메모리 캐시에서 부분 일치 검색 (DB 쿼리 없음)
        return drugInfoCache.values().parallelStream()
                .filter(drug -> {
                    String itemName = drug.getItemName();
                    if (itemName == null) return false;
                    // 공백 제거 후 비교
                    String normalizedName = itemName.replaceAll("\\s+", "");
                    return normalizedName.contains(normalizedKeyword);
                })
                .findFirst();
    }

    @Override
    public Optional<DrugInfo> findByEditDistance(String drugName) {
        return findByEditDistance(drugName, DEFAULT_THRESHOLD);
    }

    @Override
    public Optional<DrugInfo> findByEditDistance(String drugName, int threshold) {
        if (drugName == null || drugName.isBlank()) {
            return Optional.empty();
        }

        String queryJamo = decomposeToJamo(drugName);
        log.debug("편집거리 검색: '{}' → 자모: '{}'", drugName, queryJamo);

        // 병렬 스트림으로 최소 편집거리 약물 탐색
        record DrugDistance(String itemSeq, int distance) {}

        Optional<DrugDistance> bestMatch = jamoCache.entrySet().parallelStream()
                .map(entry -> new DrugDistance(
                        entry.getKey(),
                        levenshteinDistance(queryJamo, entry.getValue())
                ))
                .filter(dd -> dd.distance <= threshold)
                .min((a, b) -> Integer.compare(a.distance, b.distance));

        if (bestMatch.isPresent()) {
            DrugDistance match = bestMatch.get();
            String matchedName = nameCache.get(match.itemSeq);
            log.info("편집거리 매칭 성공: '{}' → '{}' (거리: {})",
                    drugName, matchedName, match.distance);
            // 캐시에서 조회 (DB 쿼리 없음)
            return Optional.ofNullable(drugInfoCache.get(match.itemSeq));
        }

        log.debug("편집거리 매칭 실패: '{}' (임계값 {} 이내 결과 없음)", drugName, threshold);
        return Optional.empty();
    }

    @Override
    public void refreshCache() {
        log.info("약물 캐시 갱신 시작...");
        jamoCache.clear();
        nameCache.clear();
        drugInfoCache.clear();
        searchResultCache.invalidateAll();
        searchCountCache.invalidateAll();
        init();
    }

    @Override
    public int getCacheSize() {
        return jamoCache.size();
    }

    @Override
    public List<DrugInfo> searchFromCache(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String normalizedKeyword = keyword.replaceAll("\\s+", "").toLowerCase();
        String cacheKey = normalizedKeyword + ":" + page + ":" + size;

        // Caffeine 캐시에서 조회, 없으면 계산 후 캐싱
        return searchResultCache.get(cacheKey, key -> {
            // prefix 매칭과 contains 매칭을 분리하여 관련도 정렬
            // 1순위: 약물명이 키워드로 시작하는 것
            // 2순위: displayName이 키워드로 시작하는 것
            // 3순위: 약물명에 키워드가 포함된 것
            // 4순위: displayName에 키워드가 포함된 것
            return drugInfoCache.values().parallelStream()
                    .filter(drug -> matchesKeyword(drug, normalizedKeyword))
                    .sorted(Comparator.comparingInt((DrugInfo drug) -> getRelevanceScore(drug, normalizedKeyword))
                            .thenComparing(DrugInfo::getItemName))
                    .skip((long) page * size)
                    .limit(size)
                    .toList();
        });
    }

    @Override
    public long countFromCache(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }

        String normalizedKeyword = keyword.replaceAll("\\s+", "").toLowerCase();

        // Caffeine 캐시에서 조회, 없으면 계산 후 캐싱
        return searchCountCache.get(normalizedKeyword, key ->
                drugInfoCache.values().parallelStream()
                        .filter(drug -> matchesKeyword(drug, key))
                        .count()
        );
    }

    /**
     * 약물이 키워드와 매칭되는지 확인
     */
    private boolean matchesKeyword(DrugInfo drug, String normalizedKeyword) {
        String itemName = drug.getItemName();
        String displayName = drug.getDisplayName();

        if (itemName != null) {
            String normalizedName = itemName.replaceAll("\\s+", "").toLowerCase();
            if (normalizedName.contains(normalizedKeyword)) {
                return true;
            }
        }

        if (displayName != null) {
            String normalizedDisplayName = displayName.replaceAll("\\s+", "").toLowerCase();
            if (normalizedDisplayName.contains(normalizedKeyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 관련도 점수 계산 (낮을수록 관련도 높음)
     * 0: itemName이 키워드로 시작
     * 1: displayName이 키워드로 시작
     * 2: itemName에 키워드 포함
     * 3: displayName에 키워드 포함
     */
    private int getRelevanceScore(DrugInfo drug, String normalizedKeyword) {
        String itemName = drug.getItemName();
        String displayName = drug.getDisplayName();

        if (itemName != null) {
            String normalizedName = itemName.replaceAll("\\s+", "").toLowerCase();
            if (normalizedName.startsWith(normalizedKeyword)) {
                return 0;
            }
        }

        if (displayName != null) {
            String normalizedDisplayName = displayName.replaceAll("\\s+", "").toLowerCase();
            if (normalizedDisplayName.startsWith(normalizedKeyword)) {
                return 1;
            }
        }

        if (itemName != null) {
            String normalizedName = itemName.replaceAll("\\s+", "").toLowerCase();
            if (normalizedName.contains(normalizedKeyword)) {
                return 2;
            }
        }

        return 3;
    }

    /**
     * 한글 문자열을 자모 단위로 분해
     *
     * 유니코드 NFD 정규화를 사용하여 한글을 자모로 분리합니다.
     * 비한글 문자(영문, 숫자 등)는 그대로 유지합니다.
     *
     * @param text 원본 문자열
     * @return 자모 분해된 문자열
     */
    private String decomposeToJamo(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // NFD 정규화로 한글을 자모로 분해
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);

        // Hangul Jamo 영역(U+1100~U+11FF)의 문자를 호환 자모(U+3130~U+318F)로 변환
        StringBuilder result = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (c >= 0x1100 && c <= 0x1112) {
                // 초성 (ㄱ=0x1100 → ㄱ=0x3131)
                result.append((char) (c - 0x1100 + 0x3131));
            } else if (c >= 0x1161 && c <= 0x1175) {
                // 중성 (ㅏ=0x1161 → ㅏ=0x314F)
                result.append((char) (c - 0x1161 + 0x314F));
            } else if (c >= 0x11A8 && c <= 0x11C2) {
                // 종성 (ㄱ=0x11A8 → ㄱ=0x3131)
                result.append((char) (c - 0x11A8 + 0x3131));
            } else if (!Character.isHighSurrogate(c) && !Character.isLowSurrogate(c)) {
                // 비한글 문자는 그대로 유지 (서로게이트 페어 제외)
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Levenshtein Distance (편집거리) 계산
     *
     * 두 문자열 간의 최소 편집 횟수(삽입, 삭제, 교체)를 계산합니다.
     *
     * @param s1 첫 번째 문자열
     * @param s2 두 번째 문자열
     * @return 편집거리
     */
    private int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        // 초기화
        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        // DP 계산
        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            // 행 교환
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[s2.length()];
    }

    /**
     * 약물명에서 괄호 안 성분명 제거
     * 예: "메드론정(메틸프레드니솔론)" → "메드론정"
     */
    private String extractPureDrugName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return itemName;
        }
        return itemName.replaceAll("\\([^)]*\\)", "").trim();
    }
}