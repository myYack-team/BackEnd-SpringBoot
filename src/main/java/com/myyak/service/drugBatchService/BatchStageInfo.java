package com.myyak.service.drugBatchService;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 배치 작업의 단계별 진행 정보
 */
@Getter
@Builder
public class BatchStageInfo {
    private final int stage;
    private final String name;
    private BatchJobStatus status;
    private int processedCount;
    private Integer totalCount;
    private int progress;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public void start() {
        this.status = BatchJobStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void updateProgress(int processedCount, Integer totalCount) {
        this.processedCount = processedCount;
        this.totalCount = totalCount;
        if (totalCount != null && totalCount > 0) {
            this.progress = (int) ((processedCount * 100.0) / totalCount);
        }
    }

    public void complete() {
        this.status = BatchJobStatus.COMPLETED;
        this.progress = 100;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = BatchJobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public static BatchStageInfo create(int stage, String name) {
        return BatchStageInfo.builder()
                .stage(stage)
                .name(name)
                .status(BatchJobStatus.PENDING)
                .processedCount(0)
                .totalCount(null)
                .progress(0)
                .startedAt(null)
                .completedAt(null)
                .build();
    }
}
