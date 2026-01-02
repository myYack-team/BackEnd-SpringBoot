package com.myyak.service.analysisService;

import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;

/**
 * AI 약물 분석 서비스 인터페이스
 */
public interface AnalysisService {

    /**
     * 분석 요청 (LLM 호출)
     * @param userId 사용자 ID
     * @return 분석 결과
     */
    AnalysisResponseDTO.AnalysisResult requestAnalysis(Long userId);

    /**
     * 레포트 목록 조회
     * @param userId 사용자 ID
     * @return 레포트 목록
     */
    AnalysisResponseDTO.ReportList getReportList(Long userId);

    /**
     * 레포트 상세 조회
     * @param userId 사용자 ID
     * @param reportId 레포트 ID
     * @return 분석 결과
     */
    AnalysisResponseDTO.AnalysisResult getReportDetail(Long userId, Long reportId);

    /**
     * 레포트 삭제
     * @param userId 사용자 ID
     * @param reportId 레포트 ID
     */
    void deleteReport(Long userId, Long reportId);

    /**
     * 분석 쿼터 조회
     * @param userId 사용자 ID
     * @return 쿼터 정보
     */
    AnalysisResponseDTO.QuotaInfo getQuotaInfo(Long userId);
}
