package com.myyak.repository;

import com.myyak.domain.DrugEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DrugEmbeddingRepository extends JpaRepository<DrugEmbedding, Long> {

    // 품목기준코드로 임베딩 조회
    Optional<DrugEmbedding> findByItemSeq(String itemSeq);

    // 품목기준코드 존재 여부 확인
    boolean existsByItemSeq(String itemSeq);

    // 약물명으로 검색 (부분 일치)
    List<DrugEmbedding> findByItemNameContaining(String itemName);

    // 임베딩이 존재하지 않는 약물 코드 목록 조회 (배치용)
    @Query("SELECT d.itemSeq FROM DrugInfo d WHERE d.itemSeq NOT IN (SELECT e.itemSeq FROM DrugEmbedding e)")
    List<String> findItemSeqsWithoutEmbedding();

    // 전체 임베딩 수 조회
    long count();

    // 품목기준코드 목록으로 존재 여부 확인
    @Query("SELECT e.itemSeq FROM DrugEmbedding e WHERE e.itemSeq IN :itemSeqs")
    List<String> findExistingItemSeqs(@Param("itemSeqs") List<String> itemSeqs);
}
