package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.service.supplementService.SupplementService;
import com.myyak.web.dto.SupplementDTO.SupplementRequestDTO;
import com.myyak.web.dto.SupplementDTO.SupplementResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Supplement", description = "영양제 관리 API")
@RestController
@RequestMapping("/api/supplements")
@RequiredArgsConstructor
public class SupplementController {

    private final SupplementService supplementService;

    // ============ Supplement (마스터) API ============

    @Operation(summary = "영양제 등록", description = "새로운 영양제를 마스터 테이블에 등록합니다. 다른 사용자들도 검색하여 선택할 수 있습니다.")
    @PostMapping
    public ApiResponse<SupplementResponseDTO.CreateSupplementResult> createSupplement(
            Authentication authentication,
            @Valid @RequestBody SupplementRequestDTO.CreateSupplementRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.SUPPLEMENT_CREATED, supplementService.createSupplement(userId, request));
    }

    @Operation(summary = "영양제 검색", description = "영양제를 검색합니다. 키워드와 태그로 필터링할 수 있습니다.")
    @GetMapping("/search")
    public ApiResponse<SupplementResponseDTO.SupplementList> searchSupplements(
            @Parameter(description = "검색 키워드")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "영양제 태그")
            @RequestParam(required = false) SupplementTag tag,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess(supplementService.searchSupplements(keyword, tag, page, size));
    }

    @Operation(summary = "인기 영양제 조회", description = "선택 횟수가 많은 인기 영양제를 조회합니다.")
    @GetMapping("/popular")
    public ApiResponse<SupplementResponseDTO.SupplementList> getPopularSupplements(
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess(supplementService.getPopularSupplements(page, size));
    }

    @Operation(summary = "영양제 상세 조회", description = "영양제의 상세 정보를 조회합니다.")
    @GetMapping("/{supplementId}")
    public ApiResponse<SupplementResponseDTO.SupplementDetail> getSupplementDetail(
            @Parameter(description = "영양제 ID")
            @PathVariable Long supplementId) {
        return ApiResponse.onSuccess(supplementService.getSupplementDetail(supplementId));
    }

    @Operation(summary = "영양제 수정", description = "본인이 등록한 영양제 정보를 수정합니다.")
    @PatchMapping("/{supplementId}")
    public ApiResponse<SupplementResponseDTO.SupplementDetail> updateSupplement(
            Authentication authentication,
            @Parameter(description = "영양제 ID")
            @PathVariable Long supplementId,
            @RequestBody SupplementRequestDTO.UpdateSupplementRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.SUPPLEMENT_UPDATED, supplementService.updateSupplement(userId, supplementId, request));
    }

    @Operation(summary = "영양제 삭제", description = "본인이 등록한 영양제를 삭제합니다.")
    @DeleteMapping("/{supplementId}")
    public ApiResponse<Void> deleteSupplement(
            Authentication authentication,
            @Parameter(description = "영양제 ID")
            @PathVariable Long supplementId) {
        Long userId = (Long) authentication.getPrincipal();
        supplementService.deleteSupplement(userId, supplementId);
        return ApiResponse.of(SuccessStatus.SUPPLEMENT_DELETED, null);
    }

    // ============ UserSupplement (내 영양제) API ============

    @Operation(summary = "내 영양제 추가", description = "영양제를 내 복용 목록에 추가합니다.")
    @PostMapping("/my")
    public ApiResponse<SupplementResponseDTO.AddUserSupplementResult> addUserSupplement(
            Authentication authentication,
            @Valid @RequestBody SupplementRequestDTO.AddUserSupplementRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.USER_SUPPLEMENT_CREATED, supplementService.addUserSupplement(userId, request));
    }

    @Operation(summary = "내 영양제 목록 조회", description = "내 영양제 목록을 조회합니다.")
    @GetMapping("/my")
    public ApiResponse<SupplementResponseDTO.UserSupplementList> getUserSupplements(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(supplementService.getUserSupplements(userId));
    }

    @Operation(summary = "내 영양제 상세 조회", description = "내 영양제의 상세 정보를 조회합니다.")
    @GetMapping("/my/{userSupplementId}")
    public ApiResponse<SupplementResponseDTO.UserSupplementDetail> getUserSupplementDetail(
            Authentication authentication,
            @Parameter(description = "사용자 영양제 ID")
            @PathVariable Long userSupplementId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(supplementService.getUserSupplementDetail(userId, userSupplementId));
    }

    @Operation(summary = "내 영양제 수정", description = "내 영양제의 복용 정보를 수정합니다.")
    @PatchMapping("/my/{userSupplementId}")
    public ApiResponse<SupplementResponseDTO.UpdateUserSupplementResult> updateUserSupplement(
            Authentication authentication,
            @Parameter(description = "사용자 영양제 ID")
            @PathVariable Long userSupplementId,
            @RequestBody SupplementRequestDTO.UpdateUserSupplementRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.USER_SUPPLEMENT_UPDATED, supplementService.updateUserSupplement(userId, userSupplementId, request));
    }

    @Operation(summary = "내 영양제 삭제", description = "내 영양제를 복용 목록에서 삭제합니다.")
    @DeleteMapping("/my/{userSupplementId}")
    public ApiResponse<Void> deleteUserSupplement(
            Authentication authentication,
            @Parameter(description = "사용자 영양제 ID")
            @PathVariable Long userSupplementId) {
        Long userId = (Long) authentication.getPrincipal();
        supplementService.deleteUserSupplement(userId, userSupplementId);
        return ApiResponse.of(SuccessStatus.USER_SUPPLEMENT_DELETED, null);
    }
}
