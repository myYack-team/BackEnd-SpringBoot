package com.myyak.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.service.prescriptionService.PrescriptionService;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionRequestDTO;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Tag(name = "Prescription", description = "처방전 관리 API")
@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "처방전 이미지 업로드", description = "처방전 이미지를 업로드하고 처방 기록을 생성합니다")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PrescriptionResponseDTO.UploadResult> uploadPrescription(
            @Parameter(description = "사용자 ID") @RequestParam Long userId,
            @Parameter(description = "처방전 이미지") @RequestPart("file") MultipartFile file,
            @Parameter(description = "처방 날짜 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prescriptionDate) {

        PrescriptionResponseDTO.UploadResult result = prescriptionService.uploadPrescription(userId, file, prescriptionDate);
        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "처방전 + 약물 일괄 등록", description = "처방전 이미지와 약물 정보를 한번에 등록합니다. 실패 시 전체 롤백됩니다.")
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PrescriptionResponseDTO.RegisterResult> registerPrescription(
            @Parameter(description = "사용자 ID") @RequestParam Long userId,
            @Parameter(description = "처방전 이미지") @RequestPart("file") MultipartFile file,
            @Parameter(description = "처방전 및 약물 정보 (JSON 문자열)") @RequestPart("data") String dataJson) {

        try {
            PrescriptionRequestDTO.RegisterRequest request = objectMapper.readValue(dataJson, PrescriptionRequestDTO.RegisterRequest.class);
            PrescriptionResponseDTO.RegisterResult result = prescriptionService.registerPrescription(userId, file, request);
            return ApiResponse.onSuccess(result);
        } catch (Exception e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }
    }

    @Operation(summary = "처방전 목록 조회", description = "사용자의 모든 처방전 목록을 조회합니다")
    @GetMapping
    public ApiResponse<PrescriptionResponseDTO.PrescriptionList> getPrescriptionList(
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {

        PrescriptionResponseDTO.PrescriptionList list = prescriptionService.getPrescriptionList(userId);
        return ApiResponse.onSuccess(list);
    }

    @Operation(summary = "처방전 상세 조회", description = "특정 처방전의 상세 정보와 연결된 약품 목록을 조회합니다")
    @GetMapping("/{prescriptionId}")
    public ApiResponse<PrescriptionResponseDTO.PrescriptionDetail> getPrescriptionDetail(
            @Parameter(description = "사용자 ID") @RequestParam Long userId,
            @Parameter(description = "처방전 ID") @PathVariable Long prescriptionId) {

        PrescriptionResponseDTO.PrescriptionDetail detail = prescriptionService.getPrescriptionDetail(userId, prescriptionId);
        return ApiResponse.onSuccess(detail);
    }

    @Operation(summary = "처방전 정보 수정", description = "처방전의 날짜, 병원명, 메모를 수정합니다")
    @PatchMapping("/{prescriptionId}")
    public ApiResponse<PrescriptionResponseDTO.PrescriptionInfo> updatePrescription(
            @Parameter(description = "사용자 ID") @RequestParam Long userId,
            @Parameter(description = "처방전 ID") @PathVariable Long prescriptionId,
            @RequestBody PrescriptionRequestDTO.UpdateRequest request) {

        PrescriptionResponseDTO.PrescriptionInfo info = prescriptionService.updatePrescription(userId, prescriptionId, request);
        return ApiResponse.onSuccess(info);
    }

    @Operation(summary = "처방전 삭제", description = "처방전을 삭제합니다. 연결된 약품들의 처방전 연결이 해제됩니다.")
    @DeleteMapping("/{prescriptionId}")
    public ApiResponse<String> deletePrescription(
            @Parameter(description = "사용자 ID") @RequestParam Long userId,
            @Parameter(description = "처방전 ID") @PathVariable Long prescriptionId) {

        prescriptionService.deletePrescription(userId, prescriptionId);
        return ApiResponse.onSuccess("처방전이 삭제되었습니다.");
    }
}
