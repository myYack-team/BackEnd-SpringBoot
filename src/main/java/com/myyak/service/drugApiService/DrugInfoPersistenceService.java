package com.myyak.service.drugApiService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.DrugInfoTest;

import java.util.List;

/**
 * 약물 정보 저장 전담 서비스
 *
 * 배치 수집 루프(수천 페이지, 페이지마다 API 호출 + 1초 대기)가 단일 트랜잭션으로
 * DB 커넥션을 장시간 점유하지 않도록, 페이지 단위 저장을 별도 트랜잭션으로 분리합니다.
 *
 * 같은 클래스 내 메서드 호출은 프록시를 타지 않아 @Transactional이 무시되므로(self-invocation),
 * 저장 로직을 별도 컴포넌트로 분리하여 호출 시 반드시 트랜잭션 경계가 적용되도록 합니다.
 */
public interface DrugInfoPersistenceService {

    /**
     * e약은요 API 수집 결과 한 페이지 저장 (기존은 updateFromApi, 신규는 saveAll)
     * @return 처리된 건수
     */
    int saveEasyDrugPage(List<DrugInfo> drugInfos);

    /**
     * 허가정보 API 수집 결과 한 페이지 저장 (기존은 updateFromPermitApi, 신규는 saveAll)
     * @return 처리된 건수
     */
    int savePermitDrugPage(List<DrugInfo> drugInfos);

    /**
     * e약은요 API 수집 결과 한 페이지 저장 - 테스트 테이블 (테스트 모드용)
     * @return 처리된 건수
     */
    int saveEasyDrugTestPage(List<DrugInfoTest> drugInfos);

    /**
     * 허가정보 API 수집 결과 한 페이지 저장 - 테스트 테이블 (테스트 모드용)
     * @return 처리된 건수
     */
    int savePermitDrugTestPage(List<DrugInfoTest> drugInfos);
}
