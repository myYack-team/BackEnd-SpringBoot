package com.myyak.service.drugBatchService;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 배치 작업 컨텍스트
 * 하나의 full-sync 작업에 대한 전체 상태를 관리
 */
@Getter
public class BatchJobContext {
    private final String jobId;
    private BatchJobStatus status;
    private int currentStage;
    private final List<BatchStageInfo> stages;
    private final LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private final boolean testMode;
    private final boolean includeEfficacyCrawling;
    private final boolean includePdfImport;
    private final String pdfFilePath;
    private final Integer maxRecords;

    private volatile boolean cancelRequested;

    private BatchJobContext(boolean testMode, boolean includeEfficacyCrawling,
                            boolean includePdfImport, String pdfFilePath, Integer maxRecords) {
        this.jobId = UUID.randomUUID().toString();
        this.status = BatchJobStatus.STARTED;
        this.currentStage = 0;
        this.stages = new ArrayList<>();
        this.startedAt = LocalDateTime.now();
        this.testMode = testMode;
        this.includeEfficacyCrawling = includeEfficacyCrawling;
        this.includePdfImport = includePdfImport;
        this.pdfFilePath = pdfFilePath;
        this.maxRecords = maxRecords;
        this.cancelRequested = false;

        initializeStages();
    }

    public static BatchJobContext create(boolean testMode, boolean includeEfficacyCrawling,
                                          boolean includePdfImport, String pdfFilePath) {
        return new BatchJobContext(testMode, includeEfficacyCrawling, includePdfImport, pdfFilePath, null);
    }

    public static BatchJobContext create(boolean testMode, boolean includeEfficacyCrawling,
                                          boolean includePdfImport, String pdfFilePath, Integer maxRecords) {
        return new BatchJobContext(testMode, includeEfficacyCrawling, includePdfImport, pdfFilePath, maxRecords);
    }

    public boolean hasMaxRecordsLimit() {
        return maxRecords != null && maxRecords > 0;
    }

    private void initializeStages() {
        stages.add(BatchStageInfo.create(1, "e약은요 API 수집"));
        stages.add(BatchStageInfo.create(2, "허가정보 API 수집"));
        stages.add(BatchStageInfo.create(3, "성분명 재파싱"));

        if (includeEfficacyCrawling) {
            stages.add(BatchStageInfo.create(4, "효능 크롤링"));
        }

        if (includePdfImport) {
            stages.add(BatchStageInfo.create(5, "PDF 배치"));
        }
    }

    public void startStage(int stage) {
        this.currentStage = stage;
        this.status = BatchJobStatus.IN_PROGRESS;
        getStageInfo(stage).start();
    }

    public void updateStageProgress(int stage, int processedCount, Integer totalCount) {
        getStageInfo(stage).updateProgress(processedCount, totalCount);
    }

    public void completeStage(int stage) {
        getStageInfo(stage).complete();
    }

    public void complete() {
        this.status = BatchJobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = BatchJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();

        // 현재 진행 중인 스테이지 실패 처리
        if (currentStage > 0) {
            getStageInfo(currentStage).fail();
        }
    }

    public void cancel() {
        this.cancelRequested = true;
        this.status = BatchJobStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public void requestCancel() {
        this.cancelRequested = true;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public BatchStageInfo getStageInfo(int stage) {
        return stages.stream()
                .filter(s -> s.getStage() == stage)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid stage: " + stage));
    }

    public String getCurrentStageName() {
        if (currentStage == 0) {
            return "대기 중";
        }
        return getStageInfo(currentStage).getName();
    }

    public int getStageProgress() {
        if (currentStage == 0) {
            return 0;
        }
        return getStageInfo(currentStage).getProgress();
    }

    public int getOverallProgress() {
        if (stages.isEmpty()) {
            return 0;
        }

        int totalProgress = 0;
        for (BatchStageInfo stage : stages) {
            totalProgress += stage.getProgress();
        }
        return totalProgress / stages.size();
    }

    public LocalDateTime getEstimatedCompletionAt() {
        // 간단한 추정: 현재까지 걸린 시간 기반
        if (status != BatchJobStatus.IN_PROGRESS || currentStage == 0) {
            return null;
        }

        int overallProgress = getOverallProgress();
        if (overallProgress <= 0) {
            return null;
        }

        long elapsedSeconds = java.time.Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        long estimatedTotalSeconds = (elapsedSeconds * 100) / overallProgress;
        long remainingSeconds = estimatedTotalSeconds - elapsedSeconds;

        return LocalDateTime.now().plusSeconds(remainingSeconds);
    }
}
