package com.myyak.web.dto.DrugBatchDTO;

import com.myyak.service.drugBatchService.BatchJobContext;
import com.myyak.service.drugBatchService.BatchStageInfo;
import com.myyak.service.drugPdfBatchService.DrugPdfBatchService;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class DrugBatchResponseDTO {

    @Getter
    @Builder
    public static class StartResponse {
        private String jobId;
        private String status;
        private String message;
        private boolean testMode;

        public static StartResponse from(BatchJobContext context) {
            return StartResponse.builder()
                    .jobId(context.getJobId())
                    .status(context.getStatus().name())
                    .message("전체 동기화 작업이 시작되었습니다.")
                    .testMode(context.isTestMode())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class StatusResponse {
        private String jobId;
        private String status;
        private int currentStage;
        private String currentStageName;
        private int stageProgress;
        private int overallProgress;
        private List<StageInfoResponse> stages;
        private LocalDateTime startedAt;
        private LocalDateTime estimatedCompletionAt;
        private String errorMessage;

        public static StatusResponse from(BatchJobContext context) {
            return StatusResponse.builder()
                    .jobId(context.getJobId())
                    .status(context.getStatus().name())
                    .currentStage(context.getCurrentStage())
                    .currentStageName(context.getCurrentStageName())
                    .stageProgress(context.getStageProgress())
                    .overallProgress(context.getOverallProgress())
                    .stages(context.getStages().stream()
                            .map(StageInfoResponse::from)
                            .collect(Collectors.toList()))
                    .startedAt(context.getStartedAt())
                    .estimatedCompletionAt(context.getEstimatedCompletionAt())
                    .errorMessage(context.getErrorMessage())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class StageInfoResponse {
        private int stage;
        private String name;
        private String status;
        private int processedCount;
        private Integer totalCount;
        private int progress;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;

        public static StageInfoResponse from(BatchStageInfo stageInfo) {
            return StageInfoResponse.builder()
                    .stage(stageInfo.getStage())
                    .name(stageInfo.getName())
                    .status(stageInfo.getStatus().name())
                    .processedCount(stageInfo.getProcessedCount())
                    .totalCount(stageInfo.getTotalCount())
                    .progress(stageInfo.getProgress())
                    .startedAt(stageInfo.getStartedAt())
                    .completedAt(stageInfo.getCompletedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class CancelResponse {
        private String jobId;
        private String status;
        private String message;

        public static CancelResponse success(String jobId) {
            return CancelResponse.builder()
                    .jobId(jobId)
                    .status("CANCEL_REQUESTED")
                    .message("작업 취소가 요청되었습니다.")
                    .build();
        }

        public static CancelResponse notFound(String jobId) {
            return CancelResponse.builder()
                    .jobId(jobId)
                    .status("NOT_FOUND")
                    .message("작업을 찾을 수 없습니다.")
                    .build();
        }
    }

    @Getter
    @Builder
    public static class VerificationResponse {
        private String jobId;
        private String verificationStatus;
        private VerificationSummary summary;
        private List<MismatchDetail> mismatches;
        private LocalDateTime verifiedAt;
    }

    @Getter
    @Builder
    public static class VerificationSummary {
        private long originalTableCount;
        private long testTableCount;
        private long matchedCount;
        private long mismatchedCount;
        private long newCount;
        private long missingCount;
    }

    @Getter
    @Builder
    public static class MismatchDetail {
        private String itemSeq;
        private String field;
        private String originalValue;
        private String testValue;
    }

    @Getter
    @Builder
    public static class CsvUploadResponse {
        private String fileName;
        private int totalCount;
        private int successCount;
        private int skipCount;
        private int failCount;
        private String summary;

        public static CsvUploadResponse from(DrugPdfBatchService.BatchResult result, String fileName) {
            return CsvUploadResponse.builder()
                    .fileName(fileName)
                    .totalCount(result.totalCount())
                    .successCount(result.successCount())
                    .skipCount(result.skipCount())
                    .failCount(result.failCount())
                    .summary(result.toSummary())
                    .build();
        }
    }
}
