package com.myyak.repository;

import com.myyak.domain.DrugFoodInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugFoodInteractionRepository extends JpaRepository<DrugFoodInteraction, Long> {

    /**
     * 약물 코드 또는 성분명으로 음식 상호작용 조회
     */
    @Query("SELECT dfi FROM DrugFoodInteraction dfi " +
           "WHERE dfi.drugItemSeq IN :itemSeqs OR dfi.ingredientName IN :ingredientNames")
    List<DrugFoodInteraction> findByDrugItemSeqsOrIngredientNames(
            @Param("itemSeqs") List<String> itemSeqs,
            @Param("ingredientNames") List<String> ingredientNames);
}
