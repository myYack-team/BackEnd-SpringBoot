package com.myyak.repository;

import com.myyak.domain.Supplement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    // 이름 또는 설명으로 검색 (페이징)
    @Query("SELECT s FROM Supplement s WHERE s.name LIKE %:keyword% OR s.description LIKE %:keyword%")
    Page<Supplement> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 캐시 초기화용: createdBy를 함께 로딩
    @Query("SELECT s FROM Supplement s JOIN FETCH s.createdBy")
    List<Supplement> findAllWithCreatedBy();
}
