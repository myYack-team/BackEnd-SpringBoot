package com.myyak.service.drugBatchService;

/**
 * 전체 동기화 실제 실행 서비스
 *
 * 오케스트레이터(DrugBatchOrchestrator)와 별도 빈으로 분리한 이유:
 * 같은 클래스 내에서 @Async 메서드를 직접 호출하면 프록시를 타지 않아
 * 비동기 실행이 무시되므로(self-invocation), 실행 로직을 별도 컴포넌트로 분리하여
 * 호출 시 반드시 @Async 프록시가 적용되도록 합니다.
 */
public interface DrugBatchExecutionService {

    /**
     * 전체 동기화 비동기 실행 (Stage 1~4)
     */
    void executeFullSync(BatchJobContext context);
}
