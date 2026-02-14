package com.myyak.service.supplementSearchService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.myyak.domain.Supplement;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.repository.SupplementRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 영양제 캐시 기반 검색 서비스 구현체
 *
 * 약물 검색과 동일한 패턴으로 메모리 캐시를 활용하여 빠른 검색을 제공합니다.
 * 영양제는 사용자가 등록/삭제할 수 있으므로 데이터 변경 시 캐시 갱신 메서드를 호출해야 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplementSearchServiceImpl implements SupplementSearchService {

    private final SupplementRepository supplementRepository;

    /**
     * 전체 Supplement 캐시 (DB 쿼리 없이 검색용)
     * Key: supplementId, Value: Supplement 객체
     */
    private final Map<Long, Supplement> supplementCache = new ConcurrentHashMap<>();

    /**
     * 검색 결과 캐시 (동일 키워드 반복 검색 최적화)
     * Key: "keyword:tag:page:size", Value: 검색 결과 리스트
     * TTL: 5분, 최대 500개 엔트리
     */
    private final Cache<String, List<Supplement>> searchResultCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * 검색 결과 카운트 캐시
     * Key: "keyword:tag", Value: 매칭된 총 개수
     * TTL: 5분, 최대 200개 엔트리
     */
    private final Cache<String, Long> searchCountCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @PostConstruct
    public void init() {
        log.info("영양제 캐시 초기화 시작...");
        long startTime = System.currentTimeMillis();

        List<Supplement> allSupplements = supplementRepository.findAllWithCreatedBy();
        for (Supplement supplement : allSupplements) {
            supplementCache.put(supplement.getId(), supplement);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("영양제 캐시 초기화 완료: {}개 영양제, {}ms", supplementCache.size(), elapsed);
    }

    @Override
    public List<Supplement> searchFromCache(String keyword, SupplementTag tag, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String cacheKey = normalizedKeyword + ":" + (tag != null ? tag.name() : "ALL") + ":" + page + ":" + size;

        return searchResultCache.get(cacheKey, key -> {
            return supplementCache.values().parallelStream()
                    .filter(supplement -> matchesFilter(supplement, normalizedKeyword, tag))
                    .sorted(Comparator.comparingInt((Supplement s) -> getRelevanceScore(s, normalizedKeyword))
                            .thenComparing(Comparator.comparingInt(Supplement::getSelectionCount).reversed())
                            .thenComparing(Supplement::getName))
                    .skip((long) page * size)
                    .limit(size)
                    .toList();
        });
    }

    @Override
    public long countFromCache(String keyword, SupplementTag tag) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String cacheKey = normalizedKeyword + ":" + (tag != null ? tag.name() : "ALL");

        return searchCountCache.get(cacheKey, key ->
                supplementCache.values().parallelStream()
                        .filter(supplement -> matchesFilter(supplement, normalizedKeyword, tag))
                        .count()
        );
    }

    @Override
    public List<Supplement> getPopularFromCache(int page, int size) {
        String cacheKey = "popular:" + page + ":" + size;

        return searchResultCache.get(cacheKey, key ->
                supplementCache.values().parallelStream()
                        .sorted(Comparator.comparingInt(Supplement::getSelectionCount).reversed()
                                .thenComparing(Supplement::getName))
                        .skip((long) page * size)
                        .limit(size)
                        .toList()
        );
    }

    @Override
    public long countAll() {
        return supplementCache.size();
    }

    @Override
    public void refreshCache() {
        log.info("영양제 캐시 갱신 시작...");
        supplementCache.clear();
        searchResultCache.invalidateAll();
        searchCountCache.invalidateAll();
        init();
    }

    @Override
    public void addOrUpdateCache(Supplement supplement) {
        supplementCache.put(supplement.getId(), supplement);
        // 검색 결과 캐시 무효화 (데이터 변경됨)
        searchResultCache.invalidateAll();
        searchCountCache.invalidateAll();
        log.debug("영양제 캐시 추가/갱신: id={}, name={}", supplement.getId(), supplement.getName());
    }

    @Override
    public void removeFromCache(Long supplementId) {
        supplementCache.remove(supplementId);
        // 검색 결과 캐시 무효화 (데이터 변경됨)
        searchResultCache.invalidateAll();
        searchCountCache.invalidateAll();
        log.debug("영양제 캐시 제거: id={}", supplementId);
    }

    @Override
    public int getCacheSize() {
        return supplementCache.size();
    }

    /**
     * 키워드 정규화 (공백 제거, 소문자 변환)
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return keyword.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 필터 조건 매칭 확인
     */
    private boolean matchesFilter(Supplement supplement, String normalizedKeyword, SupplementTag tag) {
        // 태그 필터
        if (tag != null && supplement.getTag() != tag) {
            return false;
        }

        // 키워드 필터 (빈 키워드면 전체 조회)
        if (normalizedKeyword.isEmpty()) {
            return true;
        }

        String name = supplement.getName();
        String description = supplement.getDescription();

        if (name != null) {
            String normalizedName = name.replaceAll("\\s+", "").toLowerCase();
            if (normalizedName.contains(normalizedKeyword)) {
                return true;
            }
        }

        if (description != null) {
            String normalizedDesc = description.replaceAll("\\s+", "").toLowerCase();
            if (normalizedDesc.contains(normalizedKeyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 관련도 점수 계산 (낮을수록 관련도 높음)
     * 0: 이름이 키워드로 시작
     * 1: 이름에 키워드 포함
     * 2: 설명에 키워드 포함
     */
    private int getRelevanceScore(Supplement supplement, String normalizedKeyword) {
        if (normalizedKeyword.isEmpty()) {
            return 0;
        }

        String name = supplement.getName();
        if (name != null) {
            String normalizedName = name.replaceAll("\\s+", "").toLowerCase();
            if (normalizedName.startsWith(normalizedKeyword)) {
                return 0;
            }
            if (normalizedName.contains(normalizedKeyword)) {
                return 1;
            }
        }

        return 2;
    }
}
