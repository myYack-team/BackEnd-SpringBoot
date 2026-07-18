package com.myyak.repository;

import com.myyak.domain.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

    /**
     * 여러 약물들 간의 상호작용 조회
     */
    @Query("SELECT di FROM DrugInteraction di " +
           "WHERE di.drugAItemSeq IN :itemSeqs AND di.drugBItemSeq IN :itemSeqs")
    List<DrugInteraction> findByDrugItemSeqsIn(@Param("itemSeqs") List<String> itemSeqs);
}
