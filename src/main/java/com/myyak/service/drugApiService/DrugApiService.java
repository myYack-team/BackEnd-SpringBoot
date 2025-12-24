package com.myyak.service.drugApiService;

import com.myyak.domain.DrugInfo;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;

import java.util.List;

public interface DrugApiService {

    /**
     * 약품명으로 e약은요 API 검색
     */
    List<EasyDrugApiResponse.EasyDrugItem> searchFromApi(String itemName);

    /**
     * 품목기준코드로 e약은요 API 검색
     */
    EasyDrugApiResponse.EasyDrugItem searchByItemSeqFromApi(String itemSeq);

    /**
     * 약품명으로 검색 후 DB에 저장
     */
    List<DrugInfo> searchAndSave(String itemName);

    /**
     * 품목기준코드로 검색 후 DB에 저장
     */
    DrugInfo searchByItemSeqAndSave(String itemSeq);

    /**
     * 전체 데이터 배치 수집 (페이지네이션)
     */
    int fetchAllDrugs();

    /**
     * 특정 페이지 범위의 데이터 수집
     */
    int fetchDrugsByPageRange(int startPage, int endPage, int numOfRows);

    // === 의약품 허가정보 API (전문의약품 포함) ===

    /**
     * 약품명으로 허가정보 API 검색
     */
    List<DrugPermitApiResponse.DrugPermitItem> searchFromPermitApi(String itemName);

    /**
     * 허가정보 API 전체 데이터 배치 수집
     */
    int fetchAllFromPermitApi();

    /**
     * 허가정보 API 특정 페이지 범위 수집
     */
    int fetchFromPermitApiByPageRange(int startPage, int endPage, int numOfRows);

    // === 통합 배치 API ===

    /**
     * 모든 데이터 소스에서 순차적으로 데이터 수집
     * 1. e약은요 API
     * 2. 허가정보 API
     * 3. 의약품안전나라 크롤링 (효능 보완)
     */
    void fetchFromAllSources();

    // === 배치 파싱 API ===

    /**
     * ingredientKr이 NULL인 약물의 itemName을 다시 파싱하여 업데이트
     * @return 업데이트된 약물 수
     */
    int reparseIngredientKr();

    /**
     * ingredientKr이 NULL인 약물 수 조회
     */
    long countWithoutIngredientKr();
}
