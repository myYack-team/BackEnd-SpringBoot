package com.myyak.repository;

import com.myyak.domain.DrugInfoTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 테스트용 약물 정보 Repository
 * 테스트 모드 배치 수집에 사용
 */
public interface DrugInfoTestRepository extends JpaRepository<DrugInfoTest, String> {

    /**
     * ingredientKr이 NULL인 약물 조회
     */
    List<DrugInfoTest> findByIngredientKrIsNull();

    /**
     * 전체 건수 조회
     */
    @Query("SELECT COUNT(d) FROM DrugInfoTest d")
    long countAll();
}
