package com.myyak.repository;

import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.enums.SupplementTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    // 영양제 이름으로 검색 (페이징)
    Page<Supplement> findByNameContaining(String name, Pageable pageable);

    // 태그로 검색 (페이징)
    Page<Supplement> findByTag(SupplementTag tag, Pageable pageable);

    // 이름 또는 설명으로 검색 (페이징)
    @Query("SELECT s FROM Supplement s WHERE s.name LIKE %:keyword% OR s.description LIKE %:keyword%")
    Page<Supplement> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 태그와 키워드로 검색 (페이징)
    @Query("SELECT s FROM Supplement s WHERE s.tag = :tag AND (s.name LIKE %:keyword% OR s.description LIKE %:keyword%)")
    Page<Supplement> searchByTagAndKeyword(@Param("tag") SupplementTag tag, @Param("keyword") String keyword, Pageable pageable);

    // 인기순 정렬 (선택 횟수 기준)
    @Query("SELECT s FROM Supplement s ORDER BY s.selectionCount DESC")
    Page<Supplement> findAllOrderByPopularity(Pageable pageable);

    // 특정 사용자가 등록한 영양제 목록
    List<Supplement> findByCreatedBy(User user);

    // 특정 사용자가 등록한 영양제 목록 (페이징)
    Page<Supplement> findByCreatedBy(User user, Pageable pageable);
}
