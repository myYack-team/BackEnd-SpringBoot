package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.medicationService.MedicationService;
import com.myyak.web.dto.MedicationDTO.MedicationRequestDTO;
import com.myyak.web.dto.MedicationDTO.MedicationResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Medication", description = "약 관리 API")
@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @Operation(summary = "약 등록", description = "새로운 약을 등록합니다.")
    @PostMapping
    public ApiResponse<MedicationResponseDTO.CreateResult> createMedication(
            Authentication authentication,
            @Valid @RequestBody MedicationRequestDTO.CreateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.MEDICATION_CREATED, medicationService.createMedication(userId, request));
    }

    @Operation(summary = "약 목록 조회", description = "사용자의 약 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<MedicationResponseDTO.MedicationList> getMedications(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(medicationService.getMedications(userId));
    }

    @Operation(summary = "약 상세 조회", description = "특정 약의 상세 정보를 조회합니다.")
    @GetMapping("/{medicationId}")
    public ApiResponse<MedicationResponseDTO.MedicationDetail> getMedicationDetail(
            Authentication authentication,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(medicationService.getMedicationDetail(userId, medicationId));
    }

    @Operation(summary = "약 정보 수정", description = "약 정보를 수정합니다.")
    @PatchMapping("/{medicationId}")
    public ApiResponse<MedicationResponseDTO.UpdateResult> updateMedication(
            Authentication authentication,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId,
            @RequestBody MedicationRequestDTO.UpdateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.MEDICATION_UPDATED, medicationService.updateMedication(userId, medicationId, request));
    }

    @Operation(summary = "약 삭제", description = "약을 삭제합니다.")
    @DeleteMapping("/{medicationId}")
    public ApiResponse<Void> deleteMedication(
            Authentication authentication,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId) {
        Long userId = (Long) authentication.getPrincipal();
        medicationService.deleteMedication(userId, medicationId);
        return ApiResponse.of(SuccessStatus.MEDICATION_DELETED, null);
    }

    @Operation(summary = "약 일괄 삭제", description = "여러 약을 한 번에 삭제합니다.")
    @DeleteMapping("/batch")
    public ApiResponse<MedicationResponseDTO.BatchDeleteResult> deleteMedicationsBatch(
            Authentication authentication,
            @Valid @RequestBody MedicationRequestDTO.BatchDeleteRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.MEDICATION_DELETED, medicationService.deleteMedicationsBatch(userId, request.getIds()));
    }

    @Operation(summary = "중복 약물 체크", description = "등록하려는 약물이 이미 복용 중인지 확인합니다.")
    @PostMapping("/check-duplicates")
    public ApiResponse<MedicationResponseDTO.DuplicateCheckResult> checkDuplicates(
            Authentication authentication,
            @Valid @RequestBody MedicationRequestDTO.DuplicateCheckRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(medicationService.checkDuplicates(userId, request.getDrugItemSeqs()));
    }
}
