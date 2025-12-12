package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.scanService.ScanService;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Scan", description = "처방전 스캔 API")
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @Operation(
            summary = "처방전 스캔",
            description = "처방전/약봉투 이미지를 분석하여 약 정보를 추출합니다. " +
                    "DB 키워드 검색으로 약물을 매칭하고, 매칭 실패 시 자모 기반 편집거리 검색으로 OCR 오타를 보정합니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ScanResponseDTO.ScanResult> scanPrescription(
            @Parameter(description = "처방전/약봉투 이미지")
            @RequestPart("image") MultipartFile image) {
        ScanResponseDTO.ScanResult result = scanService.scanPrescription(image);

        if ("low".equals(result.getConfidence())) {
            return ApiResponse.of(SuccessStatus.SCAN_RETRY_RECOMMENDED, result);
        }
        return ApiResponse.of(SuccessStatus.SCAN_SUCCESS, result);
    }
}
