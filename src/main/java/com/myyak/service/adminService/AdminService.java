package com.myyak.service.adminService;

import com.myyak.web.dto.AdminDTO.AdminRequestDTO;
import com.myyak.web.dto.AdminDTO.AdminResponseDTO;

public interface AdminService {

    /**
     * 약물 데이터 통계 조회
     */
    AdminResponseDTO.DrugStats getDrugStats();

    /**
     * 최근 등록 영양제 목록 조회
     */
    AdminResponseDTO.SupplementList getRecentSupplements(int page, int size, Integer days, String search);

    /**
     * 태그별 영양제 통계 조회
     */
    AdminResponseDTO.SupplementTagStats getSupplementTagStats();

    /**
     * 가입자 통계 조회
     */
    AdminResponseDTO.UserStats getUserStats();

    /**
     * 일별 가입 추이 조회
     */
    AdminResponseDTO.DailySignups getDailySignups(int days);

    /**
     * 영양제 삭제 (관리자 전용)
     * - 해당 영양제를 선택한 UserSupplement도 함께 삭제
     */
    AdminResponseDTO.SupplementDeleteResult deleteSupplement(Long supplementId);

    /**
     * 서버 헬스 상태 확인
     */
    AdminResponseDTO.HealthStatus checkHealth();

    /**
     * 에러 로그 목록 조회
     */
    AdminResponseDTO.ErrorLogList getErrorLogs(int page, int size, String level, Integer hours);

    /**
     * AI 채팅 (에러 분석)
     */
    AdminResponseDTO.ChatResponse chat(AdminRequestDTO.ChatRequest request);

    /**
     * 사용자 목록 조회 (페이징, 검색)
     */
    AdminResponseDTO.UserList getUserList(int page, int size, String search);

    /**
     * 사용자 일괄 탈퇴 (관리자 전용)
     */
    AdminResponseDTO.BatchDeleteUsersResult batchDeleteUsers(AdminRequestDTO.BatchDeleteUsersRequest request);
}
