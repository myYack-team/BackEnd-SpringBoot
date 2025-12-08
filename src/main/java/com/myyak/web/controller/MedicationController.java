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
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Valid @RequestBody MedicationRequestDTO.CreateRequest request) {
        return ApiResponse.of(SuccessStatus.MEDICATION_CREATED, medicationService.createMedication(userId, request));
    }

    @Operation(summary = "약 목록 조회", description = "사용자의 약 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<MedicationResponseDTO.MedicationList> getMedications(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId) {
        return ApiResponse.onSuccess(medicationService.getMedications(userId));
    }

    @Operation(summary = "약 상세 조회", description = "특정 약의 상세 정보를 조회합니다.")
    @GetMapping("/{medicationId}")
    public ApiResponse<MedicationResponseDTO.MedicationDetail> getMedicationDetail(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId) {
        return ApiResponse.onSuccess(medicationService.getMedicationDetail(userId, medicationId));
    }

    @Operation(summary = "약 정보 수정", description = "약 정보를 수정합니다.")
    @PatchMapping("/{medicationId}")
    public ApiResponse<MedicationResponseDTO.UpdateResult> updateMedication(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId,
            @RequestBody MedicationRequestDTO.UpdateRequest request) {
        return ApiResponse.of(SuccessStatus.MEDICATION_UPDATED, medicationService.updateMedication(userId, medicationId, request));
    }

    @Operation(summary = "약 삭제", description = "약을 삭제합니다.")
    @DeleteMapping("/{medicationId}")
    public ApiResponse<Void> deleteMedication(
            @Parameter(description = "사용자 ID (임시)")
            @RequestParam Long userId,
            @Parameter(description = "약 ID")
            @PathVariable Long medicationId) {
        medicationService.deleteMedication(userId, medicationId);
        return ApiResponse.of(SuccessStatus.MEDICATION_DELETED, null);
    }
}
