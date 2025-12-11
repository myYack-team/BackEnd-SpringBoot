package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.scanService.ScanService;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Tag(name = "Scan", description = "처방전 스캔 API")
@RestController
@RequestMapping("/api/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    // 디버그용 이미지 저장 경로
    private static final String DEBUG_IMAGE_PATH = "C:/tmp/scan_debug";

    @Operation(summary = "처방전 스캔 (LLM 전용)", description = "처방전/약봉투 이미지를 분석하여 약 정보를 추출합니다. DB 키워드 검색으로 약물을 매칭합니다.")
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

    @Operation(
            summary = "처방전 스캔 (LLM + Embedding)",
            description = "처방전/약봉투 이미지를 분석하여 약 정보를 추출합니다. " +
                    "DB 키워드 검색으로 약물을 매칭하고, 매칭 실패 시 Embedding 기반 유사 약물을 추천합니다."
    )
    @PostMapping(value = "/with-embedding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ScanResponseDTO.ScanResult> scanPrescriptionWithEmbedding(
            @Parameter(description = "처방전/약봉투 이미지")
            @RequestPart("image") MultipartFile image) {

        // 디버그: 전송된 이미지 저장
        saveDebugImage(image);

        ScanResponseDTO.ScanResult result = scanService.scanPrescriptionWithEmbedding(image);

        if ("low".equals(result.getConfidence())) {
            return ApiResponse.of(SuccessStatus.SCAN_RETRY_RECOMMENDED, result);
        }
        return ApiResponse.of(SuccessStatus.SCAN_SUCCESS, result);
    }

    /**
     * 디버그용: 전송된 이미지를 파일로 저장
     */
    private void saveDebugImage(MultipartFile image) {
        try {
            Path debugDir = Paths.get(DEBUG_IMAGE_PATH);
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String originalName = image.getOriginalFilename();
            String extension = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : ".jpg";

            String fileName = "scan_" + timestamp + extension;
            Path filePath = debugDir.resolve(fileName);

            Files.write(filePath, image.getBytes());

            log.info("[DEBUG] 스캔 이미지 저장: {} ({}x{} bytes)",
                    filePath.toAbsolutePath(),
                    image.getSize(),
                    image.getContentType());

        } catch (IOException e) {
            log.warn("[DEBUG] 이미지 저장 실패: {}", e.getMessage());
        }
    }
}
