package com.myyak.repository;

import com.myyak.domain.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

    /**
     * 두 약물 간 상호작용 조회 (양방향)
     */
    @Query("SELECT di FROM DrugInteraction di " +
           "WHERE (di.drugAItemSeq = :itemSeq1 AND di.drugBItemSeq = :itemSeq2) " +
           "   OR (di.drugAItemSeq = :itemSeq2 AND di.drugBItemSeq = :itemSeq1)")
    Optional<DrugInteraction> findByDrugPair(@Param("itemSeq1") String itemSeq1,
                                              @Param("itemSeq2") String itemSeq2);

    /**
     * 특정 약물과 관련된 모든 상호작용 조회
     */
    @Query("SELECT di FROM DrugInteraction di " +
           "WHERE di.drugAItemSeq = :itemSeq OR di.drugBItemSeq = :itemSeq")
    List<DrugInteraction> findByDrugItemSeq(@Param("itemSeq") String itemSeq);

    /**
     * 여러 약물들 간의 상호작용 조회
     */
    @Query("SELECT di FROM DrugInteraction di " +
           "WHERE di.drugAItemSeq IN :itemSeqs AND di.drugBItemSeq IN :itemSeqs")
    List<DrugInteraction> findByDrugItemSeqsIn(@Param("itemSeqs") List<String> itemSeqs);
}
