package com.myyak.repository;

import com.myyak.domain.AnalysisTemporaryNote;
import com.myyak.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisTemporaryNoteRepository extends JpaRepository<AnalysisTemporaryNote, Long> {

    /**
     * 사용자의 임시 메모 목록 조회 (최신순)
     */
    List<AnalysisTemporaryNote> findByUserOrderByNoteDateDesc(User user);

    /**
     * 사용자의 임시 메모 일괄 삭제
     */
    void deleteByUser(User user);
}
