package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.adminService.AdminService;
import com.myyak.web.dto.AdminDTO.AdminRequestDTO;
import com.myyak.web.dto.AdminDTO.AdminResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 전용 API 컨트롤러
 */
@Slf4j
@Tag(name = "Admin", description = "관리자 전용 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(
            summary = "약물 데이터 통계 조회",
            description = "전체 약물 수, 효능 미수집 수, 성분명 미파싱 수를 조회합니다."
    )
    @GetMapping("/stats/drugs")
    public ApiResponse<AdminResponseDTO.DrugStats> getDrugStats() {
        return ApiResponse.onSuccess(adminService.getDrugStats());
    }

    @Operation(
            summary = "최근 등록 영양제 목록 조회",
            description = "최근 등록된 영양제 목록을 페이징하여 조회합니다."
    )
    @GetMapping("/supplements/recent")
    public ApiResponse<AdminResponseDTO.SupplementList> getRecentSupplements(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "최근 N일 (null이면 전체)") @RequestParam(required = false) Integer days,
            @Parameter(description = "검색어") @RequestParam(required = false) String search) {
        return ApiResponse.onSuccess(adminService.getRecentSupplements(page, size, days, search));
    }

    @Operation(
            summary = "태그별 영양제 통계 조회",
            description = "영양제 태그별 개수를 조회합니다."
    )
    @GetMapping("/supplements/stats/tags")
    public ApiResponse<AdminResponseDTO.SupplementTagStats> getSupplementTagStats() {
        return ApiResponse.onSuccess(adminService.getSupplementTagStats());
    }

    @Operation(
            summary = "가입자 통계 조회",
            description = "전체 가입자 수, 기간별 가입자 수, 성별/연령대 분포를 조회합니다."
    )
    @GetMapping("/users/stats")
    public ApiResponse<AdminResponseDTO.UserStats> getUserStats() {
        return ApiResponse.onSuccess(adminService.getUserStats());
    }

    @Operation(
            summary = "일별 가입 추이 조회",
            description = "최근 N일간 일별 가입자 수를 조회합니다."
    )
    @GetMapping("/users/daily")
    public ApiResponse<AdminResponseDTO.DailySignups> getDailySignups(
            @Parameter(description = "조회 기간 (일)") @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.onSuccess(adminService.getDailySignups(days));
    }

    @Operation(
            summary = "영양제 삭제 (관리자 전용)",
            description = """
                특정 영양제를 삭제합니다.
                해당 영양제를 선택한 사용자들의 UserSupplement도 함께 삭제됩니다.
                """
    )
    @DeleteMapping("/supplements/{id}")
    public ApiResponse<AdminResponseDTO.SupplementDeleteResult> deleteSupplement(
            @Parameter(description = "삭제할 영양제 ID") @PathVariable Long id) {
        log.info("관리자 영양제 삭제 요청: id={}", id);
        return ApiResponse.onSuccess(adminService.deleteSupplement(id));
    }

    @Operation(
            summary = "서버 헬스 상태 확인",
            description = "서버, DB, 스토리지 연결 상태 및 시스템 리소스 사용량을 조회합니다."
    )
    @GetMapping("/health")
    public ApiResponse<AdminResponseDTO.HealthStatus> getServerHealth() {
        return ApiResponse.onSuccess(adminService.checkHealth());
    }

    @Operation(
            summary = "에러 로그 목록 조회",
            description = "journalctl에서 서버 로그를 조회합니다. 레벨 필터링 및 시간 범위 지정이 가능합니다."
    )
    @GetMapping("/logs")
    public ApiResponse<AdminResponseDTO.ErrorLogList> getErrorLogs(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "로그 레벨 (ERROR, WARN, INFO 등)") @RequestParam(required = false) String level,
            @Parameter(description = "최근 N시간 (기본 24시간)") @RequestParam(required = false) Integer hours) {
        return ApiResponse.onSuccess(adminService.getErrorLogs(page, size, level, hours));
    }

    @Operation(
            summary = "AI 에러 분석 채팅",
            description = "에러 로그에 대해 AI와 대화하며 원인을 분석합니다."
    )
    @PostMapping("/logs/chat")
    public ApiResponse<AdminResponseDTO.ChatResponse> chat(
            @Valid @RequestBody AdminRequestDTO.ChatRequest request) {
        log.info("AI 채팅 요청: userMessage={}", request.getUserMessage());
        return ApiResponse.onSuccess(adminService.chat(request));
    }

    @Operation(
            summary = "사용자 목록 조회",
            description = "전체 사용자 목록을 페이징하여 조회합니다. 이름, 이메일, 카카오ID로 검색 가능합니다."
    )
    @GetMapping("/users")
    public ApiResponse<AdminResponseDTO.UserList> getUserList(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "검색어 (이름, 이메일, 카카오ID)") @RequestParam(required = false) String search) {
        return ApiResponse.onSuccess(adminService.getUserList(page, size, search));
    }

    @Operation(
            summary = "사용자 일괄 탈퇴 (관리자 전용)",
            description = """
                선택한 사용자들을 일괄 탈퇴 처리합니다.
                각 사용자의 모든 관련 데이터(약물, 영양제, 복용기록, 알림 등)가 함께 삭제됩니다.
                카카오 연동 해제도 함께 처리됩니다.
                """
    )
    @DeleteMapping("/users/batch")
    public ApiResponse<AdminResponseDTO.BatchDeleteUsersResult> batchDeleteUsers(
            @Valid @RequestBody AdminRequestDTO.BatchDeleteUsersRequest request) {
        log.info("관리자 사용자 일괄 탈퇴 요청: {} 명", request.getUserIds().size());
        return ApiResponse.onSuccess(adminService.batchDeleteUsers(request));
    }
}
