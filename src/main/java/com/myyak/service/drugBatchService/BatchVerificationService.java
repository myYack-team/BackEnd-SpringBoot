package com.myyak.service.drugBatchService;

import com.myyak.web.dto.DrugBatchDTO.DrugBatchResponseDTO;

/**
 * 배치 검증 서비스
 * 테스트 테이블과 운영 테이블의 데이터를 비교하여 검증 결과를 생성
 */
public interface BatchVerificationService {

    /**
     * 테스트 테이블과 운영 테이블 비교 검증
     */
    DrugBatchResponseDTO.VerificationResponse verify(String jobId);

    /**
     * 테스트 테이블 데이터 삭제 (검증 후 정리)
     */
    void clearTestTable();

    /**
     * 테스트 테이블 데이터를 운영 테이블로 이관
     * @return 이관된 건수
     */
    int promoteTestData();
}
