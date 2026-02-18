package com.myyak.service.analysisService;

import com.myyak.web.dto.AnalysisDTO.AnalysisRequestDTO;
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

    /**
     * AI 분석 데이터 충분성 확인
     * @param userId 사용자 ID
     * @return 데이터 충분성 정보
     */
    AnalysisResponseDTO.DataSufficiencyCheck checkDataSufficiency(Long userId);

    /**
     * 임시 건강 메모 저장
     * @param userId 사용자 ID
     * @param request 임시 메모 요청
     */
    void saveTemporaryNote(Long userId, AnalysisRequestDTO.TemporaryNoteRequest request);

    /**
     * 임시 건강 메모 일괄 삭제
     * @param userId 사용자 ID
     */
    void deleteAllTemporaryNotes(Long userId);

    /**
     * 테스트 분석 요청 (Mock 데이터 사용)
     * 데이터가 부족한 사용자가 AI 분석을 체험할 수 있도록 합니다.
     * @param userId 사용자 ID
     * @return 분석 결과
     */
    AnalysisResponseDTO.AnalysisResult requestTestAnalysis(Long userId);
}
