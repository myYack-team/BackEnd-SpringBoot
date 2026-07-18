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
 *
 * [메모리 최적화]
 * DrugInfo 엔티티 전체(효능, 부작용 등 긴 텍스트)를 캐싱하면 OOM 발생.
 * 검색에 필요한 최소 필드만 DrugSearchCache에 캐싱하고,
 * 상세 정보는 DB에서 조회합니다.
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
     * 경량 검색 캐시 (DB 쿼리 없이 검색용)
     * Key: itemSeq, Value: DrugSearchCache (검색에 필요한 최소 필드만)
     *
     * 메모리 최적화: DrugInfo 전체(효능, 부작용 등) 대신 경량 DTO 사용
     * - DrugInfo 전체: 약물 1건당 5~20KB → 10만 건 시 500MB~2GB
     * - DrugSearchCache: 약물 1건당 200~500bytes → 10만 건 시 20~50MB
     */
    private final Map<String, DrugSearchCache> drugSearchCache = new ConcurrentHashMap<>();

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

        // 검색에 필요한 필드만 조회 (메모리 최적화)
        List<Object[]> drugData = drugInfoRepository.findAllForCache();
        for (Object[] row : drugData) {
            String itemSeq = (String) row[0];
            String itemName = (String) row[1];
            String displayName = (String) row[2];
            var drugType = (com.myyak.domain.enums.DrugType) row[3];
            String imageUrl = (String) row[4];

            if (itemName != null && !itemName.isBlank()) {
                // 괄호 제거한 순수 약물명으로 자모 분해
                String pureName = extractPureDrugName(itemName);
                jamoCache.put(itemSeq, decomposeToJamo(pureName));
                nameCache.put(itemSeq, pureName);
                // 경량 캐시에 저장
                drugSearchCache.put(itemSeq, new DrugSearchCache(
                        itemSeq, itemName, displayName, drugType, imageUrl
                ));
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("약물 캐시 초기화 완료: {}개 약물, {}ms", drugSearchCache.size(), elapsed);
    }

    @Override
    public Optional<DrugInfo> findByNameContaining(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        // 공백 제거 + 소문자 변환 (캐시에 미리 계산된 정규화 이름과 동일한 규칙)
        String normalizedKeyword = DrugSearchCache.normalize(keyword);

        // 메모리 캐시에서 부분 일치 검색 (캐시 적재 시 정규화된 이름 사용 → 재계산 없음)
        Optional<DrugSearchCache> cached = drugSearchCache.values().parallelStream()
                .filter(cache -> {
                    String normalizedName = cache.getNormalizedItemName();
                    return normalizedName != null && normalizedName.contains(normalizedKeyword);
                })
                .findFirst();

        // 캐시에서 찾으면 DB에서 상세 정보 조회
        return cached.flatMap(cache -> drugInfoRepository.findById(cache.getItemSeq()));
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
            // DB에서 상세 정보 조회
            return drugInfoRepository.findById(match.itemSeq);
        }

        log.debug("편집거리 매칭 실패: '{}' (임계값 {} 이내 결과 없음)", drugName, threshold);
        return Optional.empty();
    }

    @Override
    public void refreshCache() {
        log.info("약물 캐시 갱신 시작...");
        jamoCache.clear();
        nameCache.clear();
        drugSearchCache.clear();
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

        String normalizedKeyword = DrugSearchCache.normalize(keyword);
        String cacheKey = normalizedKeyword + ":" + page + ":" + size;

        // Caffeine 캐시에서 조회, 없으면 계산 후 캐싱
        return searchResultCache.get(cacheKey, key -> {
            // 경량 캐시에서 매칭되는 itemSeq 목록 추출 (페이징 적용)
            List<String> matchedItemSeqs = drugSearchCache.values().parallelStream()
                    .filter(cache -> matchesKeyword(cache, normalizedKeyword))
                    .sorted(Comparator.comparingInt((DrugSearchCache cache) -> getRelevanceScore(cache, normalizedKeyword))
                            .thenComparing(DrugSearchCache::getItemName))
                    .skip((long) page * size)
                    .limit(size)
                    .map(DrugSearchCache::getItemSeq)
                    .toList();

            // DB에서 상세 정보 조회
            if (matchedItemSeqs.isEmpty()) {
                return List.of();
            }
            return drugInfoRepository.findAllById(matchedItemSeqs);
        });
    }

    @Override
    public long countFromCache(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }

        String normalizedKeyword = DrugSearchCache.normalize(keyword);

        // Caffeine 캐시에서 조회, 없으면 계산 후 캐싱
        return searchCountCache.get(normalizedKeyword, key ->
                drugSearchCache.values().parallelStream()
                        .filter(cache -> matchesKeyword(cache, key))
                        .count()
        );
    }

    /**
     * 약물이 키워드와 매칭되는지 확인 (경량 캐시용)
     * 캐시 적재 시 미리 계산된 정규화 이름을 사용하여 검색 시 재계산하지 않음
     */
    private boolean matchesKeyword(DrugSearchCache cache, String normalizedKeyword) {
        String normalizedName = cache.getNormalizedItemName();
        if (normalizedName != null && normalizedName.contains(normalizedKeyword)) {
            return true;
        }

        String normalizedDisplayName = cache.getNormalizedDisplayName();
        return normalizedDisplayName != null && normalizedDisplayName.contains(normalizedKeyword);
    }

    /**
     * 관련도 점수 계산 (낮을수록 관련도 높음) - 경량 캐시용
     * 0: itemName이 키워드로 시작
     * 1: displayName이 키워드로 시작
     * 2: itemName에 키워드 포함
     * 3: displayName에 키워드 포함
     */
    private int getRelevanceScore(DrugSearchCache cache, String normalizedKeyword) {
        String normalizedName = cache.getNormalizedItemName();
        String normalizedDisplayName = cache.getNormalizedDisplayName();

        if (normalizedName != null && normalizedName.startsWith(normalizedKeyword)) {
            return 0;
        }

        if (normalizedDisplayName != null && normalizedDisplayName.startsWith(normalizedKeyword)) {
            return 1;
        }

        if (normalizedName != null && normalizedName.contains(normalizedKeyword)) {
            return 2;
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
