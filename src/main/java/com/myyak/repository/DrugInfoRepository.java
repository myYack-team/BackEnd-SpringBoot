package com.myyak.repository;

import com.myyak.domain.DrugInfo;
import com.myyak.repository.projection.DrugInfoSummary;
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

    // 효능 정보가 없는 약물 조회
    @Query("SELECT d FROM DrugInfo d WHERE d.efficacy IS NULL OR d.efficacy = ''")
    List<DrugInfo> findByEfficacyIsNullOrEmpty();

    // 효능 정보가 없는 약물 수 조회
    @Query("SELECT COUNT(d) FROM DrugInfo d WHERE d.efficacy IS NULL OR d.efficacy = ''")
    long countByEfficacyIsNullOrEmpty();

    // ingredientKr이 NULL인 약물 조회
    @Query("SELECT d FROM DrugInfo d WHERE d.ingredientKr IS NULL")
    List<DrugInfo> findByIngredientKrIsNull();

    // ingredientKr이 NULL인 약물 수 조회
    @Query("SELECT COUNT(d) FROM DrugInfo d WHERE d.ingredientKr IS NULL")
    long countByIngredientKrIsNull();

    // 목록용 경량 조회 (TEXT 컬럼 제외) - Native Query
    @Query(value = "SELECT item_seq, item_name, display_name, ingredient_kr, entp_name, image_url, drug_type " +
                   "FROM drug_info WHERE item_seq IN :itemSeqs", nativeQuery = true)
    List<Object[]> findSummaryByItemSeqIn(@Param("itemSeqs") List<String> itemSeqs);

    // 목록용 경량 조회 (TEXT 컬럼 제외) - Projection 사용
    @Query("SELECT d.itemSeq as itemSeq, d.itemName as itemName, d.displayName as displayName, " +
           "d.ingredientKr as ingredientKr, d.entpName as entpName, d.imageUrl as imageUrl, d.drugType as drugType " +
           "FROM DrugInfo d WHERE d.itemSeq IN :itemSeqs")
    List<DrugInfoSummary> findSummaryProjectionByItemSeqIn(@Param("itemSeqs") List<String> itemSeqs);
}
