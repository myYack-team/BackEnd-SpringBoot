package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.converter.DrugInfoConverter;
import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.drugApiService.DrugApiService;
import com.myyak.service.drugSearchService.DrugSearchService;
import com.myyak.web.dto.DrugApiDTO.DrugApiResponseDTO;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;
import com.myyak.web.dto.DrugInfoDTO.DrugInfoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Drug API", description = "e약은요 공공데이터 API 연동")
@RestController
@RequestMapping("/api/drugs")
@RequiredArgsConstructor
public class DrugApiController {

    private final DrugApiService drugApiService;
    private final DrugInfoRepository drugInfoRepository;
    private final DrugSearchService drugSearchService;

    @Operation(summary = "약 검색 (캐시, 페이징)", description = "메모리 캐시에서 약 이름으로 검색 (DB 쿼리 없음, 매우 빠름)")
    @GetMapping("/search/fast")
    public ApiResponse<DrugInfoResponseDTO.DrugSearchPageResult> searchDrugsFast(
            @Parameter(description = "약 이름") @RequestParam String name,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {

        List<DrugInfo> drugList = drugSearchService.searchFromCache(name, page, size);
        long total = drugSearchService.countFromCache(name);

        List<DrugInfoResponseDTO.DrugInfoSummary> drugs = drugList.stream()
                .map(DrugInfoConverter::toSummary)
                .collect(Collectors.toList());

        DrugInfoResponseDTO.DrugSearchPageResult result = DrugInfoResponseDTO.DrugSearchPageResult.builder()
                .drugs(drugs)
                .page(page)
                .size(size)
                .totalCount((int) total)
                .totalPages((int) Math.ceil((double) total / size))
                .hasNext((long) (page + 1) * size < total)
                .build();

        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "약 검색 (API)", description = "e약은요 API에서 직접 검색 (DB 저장 안 함)")
    @GetMapping("/search/api")
    public ApiResponse<List<EasyDrugApiResponse.EasyDrugItem>> searchDrugsFromApi(
            @Parameter(description = "약 이름") @RequestParam String name) {

        List<EasyDrugApiResponse.EasyDrugItem> items = drugApiService.searchFromApi(name);
        return ApiResponse.onSuccess(items);
    }

    @Operation(summary = "약 검색 후 저장", description = "e약은요 API에서 검색 후 DB에 저장")
    @PostMapping("/search-and-save")
    public ApiResponse<List<DrugInfoResponseDTO.DrugInfoSummary>> searchAndSave(
            @Parameter(description = "약 이름") @RequestParam String name) {

        List<DrugInfo> savedDrugs = drugApiService.searchAndSave(name);
        List<DrugInfoResponseDTO.DrugInfoSummary> result = savedDrugs.stream()
                .map(DrugInfoConverter::toSummary)
                .collect(Collectors.toList());

        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "품목기준코드로 조회", description = "품목기준코드로 약 정보 조회 (없으면 API에서 가져옴)")
    @GetMapping("/{itemSeq}")
    public ApiResponse<DrugInfoResponseDTO.DrugInfoDetail> getDrugByItemSeq(
            @Parameter(description = "품목기준코드") @PathVariable String itemSeq) {

        DrugInfo drugInfo = drugApiService.searchByItemSeqAndSave(itemSeq);
        if (drugInfo == null) {
            return ApiResponse.onFailure("DRUG_NOT_FOUND", "약 정보를 찾을 수 없습니다.", null);
        }

        return ApiResponse.onSuccess(DrugInfoConverter.toDetail(drugInfo));
    }

    @Operation(summary = "DB 통계", description = "현재 DB에 저장된 약물 정보 통계")
    @GetMapping("/stats")
    public ApiResponse<DrugApiResponseDTO.StatsResult> getStats() {
        long count = drugInfoRepository.count();
        return ApiResponse.onSuccess(DrugApiResponseDTO.StatsResult.builder()
                .totalCount(count)
                .build());
    }

    // === 의약품 허가정보 API (전문의약품 포함) ===

    @Operation(summary = "약 검색 (허가정보 API)", description = "의약품 허가정보 API에서 직접 검색 (전문의약품 포함)")
    @GetMapping("/search/permit-api")
    public ApiResponse<List<DrugPermitApiResponse.DrugPermitItem>> searchDrugsFromPermitApi(
            @Parameter(description = "약 이름") @RequestParam String name) {

        List<DrugPermitApiResponse.DrugPermitItem> items = drugApiService.searchFromPermitApi(name);
        return ApiResponse.onSuccess(items);
    }
}
