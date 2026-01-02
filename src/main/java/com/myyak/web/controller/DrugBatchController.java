package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.drugBatchService.BatchJobContext;
import com.myyak.service.drugBatchService.BatchVerificationService;
import com.myyak.service.drugBatchService.DrugBatchOrchestrator;
import com.myyak.service.drugPdfBatchService.DrugPdfBatchService;
import com.myyak.web.dto.DrugBatchDTO.DrugBatchRequestDTO;
import com.myyak.web.dto.DrugBatchDTO.DrugBatchResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 데이터 수집 통합 배치 컨트롤러
 * 여러 데이터 소스의 수집을 통합 관리
 */
@Slf4j
@Tag(name = "Drug Batch", description = "데이터 수집 통합 배치 API")
@RestController
@RequestMapping("/api/drugs/batch")
@RequiredArgsConstructor
public class DrugBatchController {

    private final DrugBatchOrchestrator drugBatchOrchestrator;
    private final BatchVerificationService batchVerificationService;
    private final DrugPdfBatchService drugPdfBatchService;

    @Operation(
            summary = "전체 동기화 시작",
            description = """
                모든 데이터 소스에서 순차적으로 데이터를 수집합니다.

                **기본 단계 (1~3단계):**
                1. e약은요 API 수집 (30분~1시간)
                2. 허가정보 API 수집 (30분~1시간)
                3. 성분명 재파싱 (5~10분)

                **선택 단계:**
                4. 효능 크롤링 (includeEfficacyCrawling=true)
                5. PDF 배치 (includePdfImport=true, pdfFilePath 필요)

                **테스트 모드:**
                testMode=true로 설정하면 별도의 테스트 테이블에 저장하여
                기존 데이터에 영향을 주지 않고 검증할 수 있습니다.
                """
    )
    @PostMapping("/full-sync")
    public ApiResponse<DrugBatchResponseDTO.StartResponse> startFullSync(
            @RequestBody(required = false) DrugBatchRequestDTO.FullSyncRequest request) {

        if (request == null) {
            request = new DrugBatchRequestDTO.FullSyncRequest();
        }

        BatchJobContext context = drugBatchOrchestrator.startFullSync(
                request.isTestMode(),
                request.isIncludeEfficacyCrawling(),
                request.isIncludePdfImport(),
                request.getPdfFilePath(),
                request.getMaxRecords()
        );

        return ApiResponse.onSuccess(DrugBatchResponseDTO.StartResponse.from(context));
    }

    @Operation(
            summary = "진행 상태 조회",
            description = "전체 동기화 작업의 현재 진행 상태를 조회합니다."
    )
    @GetMapping("/full-sync/{jobId}/status")
    public ApiResponse<DrugBatchResponseDTO.StatusResponse> getJobStatus(
            @Parameter(description = "작업 ID") @PathVariable String jobId) {

        BatchJobContext context = drugBatchOrchestrator.getJobStatus(jobId);

        if (context == null) {
            return ApiResponse.onFailure("JOB_NOT_FOUND", "작업을 찾을 수 없습니다: " + jobId, null);
        }

        return ApiResponse.onSuccess(DrugBatchResponseDTO.StatusResponse.from(context));
    }

    @Operation(
            summary = "작업 중단",
            description = "진행 중인 전체 동기화 작업을 중단합니다. 현재 처리 중인 페이지 완료 후 중단됩니다."
    )
    @PostMapping("/full-sync/{jobId}/cancel")
    public ApiResponse<DrugBatchResponseDTO.CancelResponse> cancelJob(
            @Parameter(description = "작업 ID") @PathVariable String jobId) {

        boolean cancelled = drugBatchOrchestrator.cancelJob(jobId);

        if (cancelled) {
            return ApiResponse.onSuccess(DrugBatchResponseDTO.CancelResponse.success(jobId));
        } else {
            return ApiResponse.onFailure("CANCEL_FAILED", "작업을 취소할 수 없습니다.",
                    DrugBatchResponseDTO.CancelResponse.notFound(jobId));
        }
    }

    @Operation(
            summary = "작업 재개",
            description = "중단된 작업을 재개합니다. (미구현)"
    )
    @PostMapping("/full-sync/{jobId}/resume")
    public ApiResponse<String> resumeJob(
            @Parameter(description = "작업 ID") @PathVariable String jobId) {

        // TODO: 작업 재개 기능 구현
        return ApiResponse.onFailure("NOT_IMPLEMENTED", "작업 재개 기능은 아직 구현되지 않았습니다.", null);
    }

    @Operation(
            summary = "테스트 검증",
            description = "테스트 모드로 수집된 데이터와 기존 데이터를 비교 검증합니다."
    )
    @PostMapping("/full-sync/{jobId}/verify")
    public ApiResponse<DrugBatchResponseDTO.VerificationResponse> verifyJob(
            @Parameter(description = "작업 ID") @PathVariable String jobId) {

        BatchJobContext context = drugBatchOrchestrator.getJobStatus(jobId);

        if (context == null) {
            return ApiResponse.onFailure("JOB_NOT_FOUND", "작업을 찾을 수 없습니다: " + jobId, null);
        }

        if (!context.isTestMode()) {
            return ApiResponse.onFailure("NOT_TEST_MODE", "테스트 모드로 실행된 작업만 검증할 수 있습니다.", null);
        }

        DrugBatchResponseDTO.VerificationResponse result = batchVerificationService.verify(jobId);
        return ApiResponse.onSuccess(result);
    }

    @Operation(
            summary = "테스트 테이블 초기화",
            description = "테스트 테이블의 모든 데이터를 삭제합니다."
    )
    @DeleteMapping("/test-table")
    public ApiResponse<String> clearTestTable() {
        batchVerificationService.clearTestTable();
        return ApiResponse.onSuccess("테스트 테이블이 초기화되었습니다.");
    }

    @Operation(
            summary = "테스트 데이터 운영 이관",
            description = "테스트 테이블의 데이터를 운영 테이블로 이관합니다."
    )
    @PostMapping("/test-table/promote")
    public ApiResponse<String> promoteTestData() {
        int promotedCount = batchVerificationService.promoteTestData();
        return ApiResponse.onSuccess(promotedCount + "건의 데이터가 운영 테이블로 이관되었습니다.");
    }

    @Operation(
            summary = "CSV 파일 업로드 (PDF 배치)",
            description = """
                nedrug에서 다운로드한 CSV 파일을 업로드하여 약물 정보를 보강합니다.

                **CSV 파일 요구사항:**
                - 인코딩: UTF-8
                - 필수 컬럼: 품목일련번호
                - 선택 컬럼: 효능효과, 용법용량, 주의사항, 첨부문서, 저장방법, 유효기간

                **처리 방식:**
                - 기존 DB에 있는 품목만 업데이트
                - 빈 필드만 채움 (이미 데이터가 있으면 스킵)
                - PDF URL이 있으면 PDF를 다운로드하여 텍스트 추출
                """
    )
    @PostMapping(value = "/csv-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DrugBatchResponseDTO.CsvUploadResponse> uploadCsv(
            @Parameter(description = "CSV 파일 (.csv)")
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ApiResponse.onFailure("FILE_EMPTY", "파일이 비어있습니다.", null);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            return ApiResponse.onFailure("INVALID_FILE_TYPE", "CSV 파일만 업로드 가능합니다.", null);
        }

        try {
            log.info("CSV 파일 업로드 시작: {}, size={}bytes", originalFilename, file.getSize());

            DrugPdfBatchService.BatchResult result = drugPdfBatchService.importFromCsv(
                    file.getInputStream(),
                    originalFilename
            );

            return ApiResponse.onSuccess(DrugBatchResponseDTO.CsvUploadResponse.from(result, originalFilename));

        } catch (IOException e) {
            log.error("CSV 파일 읽기 실패: {}", e.getMessage());
            return ApiResponse.onFailure("FILE_READ_ERROR", "파일 읽기 실패: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("CSV 처리 실패: {}", e.getMessage(), e);
            return ApiResponse.onFailure("PROCESS_ERROR", "처리 실패: " + e.getMessage(), null);
        }
    }
}
