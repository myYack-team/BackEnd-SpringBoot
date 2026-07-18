package com.myyak.repository;

import com.myyak.domain.DrugInfo;
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

    /**
     * 캐시 초기화용 경량 조회 (검색에 필요한 최소 필드만)
     * 메모리 최적화: TEXT 컬럼(효능, 부작용 등) 제외
     *
     * @return Object[] = {itemSeq, itemName, displayName, drugType, imageUrl}
     */
    @Query("SELECT d.itemSeq, d.itemName, d.displayName, d.drugType, d.imageUrl FROM DrugInfo d")
    List<Object[]> findAllForCache();
}
