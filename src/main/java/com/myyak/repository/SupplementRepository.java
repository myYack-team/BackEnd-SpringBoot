package com.myyak.repository;

import com.myyak.domain.Supplement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    // 이름 또는 설명으로 검색 (페이징)
    @Query("SELECT s FROM Supplement s WHERE s.name LIKE %:keyword% OR s.description LIKE %:keyword%")
    Page<Supplement> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 캐시 초기화용: createdBy를 함께 로딩
    @Query("SELECT s FROM Supplement s JOIN FETCH s.createdBy")
    List<Supplement> findAllWithCreatedBy();

    // 특정 시점 이후 등록된 영양제 목록 (페이징)
    Page<Supplement> findByCreatedAtAfter(LocalDateTime since, Pageable pageable);

    // 이름 또는 설명으로 검색 + 등록일 필터 (페이징)
    @Query("SELECT s FROM Supplement s WHERE s.createdAt > :since AND (s.name LIKE %:keyword% OR s.description LIKE %:keyword%)")
    Page<Supplement> searchByKeywordAndCreatedAtAfter(@Param("keyword") String keyword, @Param("since") LocalDateTime since, Pageable pageable);

    // 태그별 영양제 수 집계
    @Query("SELECT s.tag, COUNT(s) FROM Supplement s GROUP BY s.tag")
    List<Object[]> countGroupByTag();
}
