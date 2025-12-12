package com.myyak.repository;

import com.myyak.domain.DrugInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DrugInfoRepository extends JpaRepository<DrugInfo, String> {

    // 약 이름으로 검색 (부분 일치)
    List<DrugInfo> findByItemNameContaining(String itemName);

    // 약 이름으로 정확히 검색
    Optional<DrugInfo> findByItemName(String itemName);

    // 제약회사명으로 검색
    List<DrugInfo> findByEntpNameContaining(String entpName);

    // 약 이름 또는 제약회사명으로 검색
    @Query("SELECT d FROM DrugInfo d WHERE d.itemName LIKE %:keyword% OR d.entpName LIKE %:keyword%")
    List<DrugInfo> searchByKeyword(@Param("keyword") String keyword);

    // 약 이름 또는 제약회사명으로 검색 (페이징)
    @Query("SELECT d FROM DrugInfo d WHERE d.itemName LIKE %:keyword% OR d.entpName LIKE %:keyword%")
    Page<DrugInfo> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 약 이름으로 검색 (대소문자 무시)
    @Query("SELECT d FROM DrugInfo d WHERE LOWER(d.itemName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<DrugInfo> findByItemNameIgnoreCase(@Param("name") String name);
}
