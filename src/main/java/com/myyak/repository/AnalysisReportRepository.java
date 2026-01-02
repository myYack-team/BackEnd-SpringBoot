package com.myyak.repository;

import com.myyak.domain.AnalysisReport;
import com.myyak.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    /**
     * 사용자의 분석 레포트 목록 (최신순)
     */
    List<AnalysisReport> findByUserOrderByAnalysisDateDesc(User user);

    /**
     * 사용자의 특정 레포트 조회 (소유권 검증)
     */
    Optional<AnalysisReport> findByIdAndUser(Long id, User user);

    /**
     * 사용자의 레포트 개수
     */
    long countByUser(User user);

    /**
     * 사용자의 레포트 삭제
     */
    void deleteByIdAndUser(Long id, User user);
}
