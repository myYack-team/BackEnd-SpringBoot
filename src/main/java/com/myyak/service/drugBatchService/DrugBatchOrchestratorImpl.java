package com.myyak.service.drugBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 데이터 수집 배치 오케스트레이터 구현체
 * 작업 컨텍스트를 관리하고 실제 실행은 DrugBatchExecutionService에 위임
 *
 * 실행 로직을 별도 빈(DrugBatchExecutionService)으로 분리한 이유:
 * 같은 클래스 내에서 @Async 메서드를 직접 호출하면 프록시를 타지 않아
 * 비동기 실행이 무시되므로(self-invocation), 별도 빈 호출로 프록시 적용을 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugBatchOrchestratorImpl implements DrugBatchOrchestrator {

    private final DrugBatchExecutionService drugBatchExecutionService;

    // 작업 상태 저장소 (메모리)
    private final Map<String, BatchJobContext> jobContexts = new ConcurrentHashMap<>();

    @Override
    public BatchJobContext startFullSync(boolean testMode, boolean includeEfficacyCrawling,
                                         boolean includePdfImport, String pdfFilePath, Integer maxRecords) {
        return startFullSync(testMode, includeEfficacyCrawling, includePdfImport, pdfFilePath, maxRecords, null, null);
    }

    @Override
    public BatchJobContext startFullSync(boolean testMode, boolean includeEfficacyCrawling,
                                         boolean includePdfImport, String pdfFilePath, Integer maxRecords,
                                         InputStream csvInputStream, String csvFileName) {
        BatchJobContext context = BatchJobContext.create(
                testMode, includeEfficacyCrawling, includePdfImport, pdfFilePath, maxRecords);

        // CSV 파일이 있으면 context에 설정
        if (csvInputStream != null && csvFileName != null) {
            context.setCsvFile(csvInputStream, csvFileName);
        }

        jobContexts.put(context.getJobId(), context);

        log.info("전체 동기화 작업 시작: jobId={}, testMode={}, maxRecords={}, hasCsvFile={}",
                context.getJobId(), testMode, maxRecords, context.hasCsvFile());

        // 비동기로 실제 작업 실행 (별도 빈 호출로 @Async 프록시 적용)
        drugBatchExecutionService.executeFullSync(context);

        return context;
    }

    @Override
    public BatchJobContext getJobStatus(String jobId) {
        return jobContexts.get(jobId);
    }

    @Override
    public boolean cancelJob(String jobId) {
        BatchJobContext context = jobContexts.get(jobId);
        if (context == null) {
            return false;
        }

        if (context.getStatus() == BatchJobStatus.IN_PROGRESS ||
            context.getStatus() == BatchJobStatus.STARTED) {
            context.requestCancel();
            log.info("작업 취소 요청됨: jobId={}", jobId);
            return true;
        }

        return false;
    }
}
