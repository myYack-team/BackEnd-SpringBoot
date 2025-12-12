package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.converter.DrugInfoConverter;
import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.drugApiService.DrugApiService;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;
import com.myyak.web.dto.DrugInfoDTO.DrugInfoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Operation(summary = "약 검색 (DB)", description = "DB에서 약 이름으로 검색")
    @GetMapping("/search")
    public ApiResponse<List<DrugInfoResponseDTO.DrugInfoSummary>> searchDrugs(
            @Parameter(description = "약 이름") @RequestParam String name) {

        List<DrugInfo> drugInfos = drugInfoRepository.searchByKeyword(name);
        List<DrugInfoResponseDTO.DrugInfoSummary> result = drugInfos.stream()
                .map(DrugInfoConverter::toSummary)
                .collect(Collectors.toList());

        return ApiResponse.onSuccess(result);
    }

    @Operation(summary = "약 검색 (DB, 페이징)", description = "DB에서 약 이름으로 검색 (페이징 지원)")
    @GetMapping("/search/page")
    public ApiResponse<DrugInfoResponseDTO.DrugSearchPageResult> searchDrugsWithPaging(
            @Parameter(description = "약 이름") @RequestParam String name,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<DrugInfo> drugPage = drugInfoRepository.searchByKeyword(name, pageable);
        DrugInfoResponseDTO.DrugSearchPageResult result = DrugInfoConverter.toSearchPageResult(drugPage);

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

    @Operation(summary = "전체 데이터 배치 수집", description = "e약은요 API에서 전체 데이터를 수집합니다 (주의: 시간이 오래 걸림)")
    @PostMapping("/batch/all")
    public ApiResponse<BatchResult> fetchAllDrugs() {
        int count = drugApiService.fetchAllDrugs();
        return ApiResponse.onSuccess(new BatchResult(count, "전체 데이터 수집 완료"));
    }

    @Operation(summary = "페이지 범위 배치 수집", description = "특정 페이지 범위의 데이터만 수집")
    @PostMapping("/batch/range")
    public ApiResponse<BatchResult> fetchDrugsByRange(
            @Parameter(description = "시작 페이지") @RequestParam(defaultValue = "1") int startPage,
            @Parameter(description = "끝 페이지") @RequestParam(defaultValue = "10") int endPage,
            @Parameter(description = "페이지당 건수") @RequestParam(defaultValue = "100") int numOfRows) {

        int count = drugApiService.fetchDrugsByPageRange(startPage, endPage, numOfRows);
        return ApiResponse.onSuccess(new BatchResult(count, "페이지 범위 수집 완료"));
    }

    @Operation(summary = "DB 통계", description = "현재 DB에 저장된 약물 정보 통계")
    @GetMapping("/stats")
    public ApiResponse<StatsResult> getStats() {
        long count = drugInfoRepository.count();
        return ApiResponse.onSuccess(new StatsResult(count));
    }

    // === 의약품 허가정보 API (전문의약품 포함) ===

    @Operation(summary = "약 검색 (허가정보 API)", description = "의약품 허가정보 API에서 직접 검색 (전문의약품 포함)")
    @GetMapping("/search/permit-api")
    public ApiResponse<List<DrugPermitApiResponse.DrugPermitItem>> searchDrugsFromPermitApi(
            @Parameter(description = "약 이름") @RequestParam String name) {

        List<DrugPermitApiResponse.DrugPermitItem> items = drugApiService.searchFromPermitApi(name);
        return ApiResponse.onSuccess(items);
    }

    @Operation(summary = "허가정보 전체 배치 수집", description = "의약품 허가정보 API에서 전체 데이터를 수집합니다 (주의: 매우 오래 걸림)")
    @PostMapping("/batch/permit/all")
    public ApiResponse<BatchResult> fetchAllFromPermitApi() {
        int count = drugApiService.fetchAllFromPermitApi();
        return ApiResponse.onSuccess(new BatchResult(count, "허가정보 전체 수집 완료"));
    }

    @Operation(summary = "허가정보 페이지 범위 배치 수집", description = "의약품 허가정보 API에서 특정 페이지 범위의 데이터만 수집")
    @PostMapping("/batch/permit/range")
    public ApiResponse<BatchResult> fetchFromPermitApiByRange(
            @Parameter(description = "시작 페이지") @RequestParam(defaultValue = "1") int startPage,
            @Parameter(description = "끝 페이지") @RequestParam(defaultValue = "10") int endPage,
            @Parameter(description = "페이지당 건수") @RequestParam(defaultValue = "100") int numOfRows) {

        int count = drugApiService.fetchFromPermitApiByPageRange(startPage, endPage, numOfRows);
        return ApiResponse.onSuccess(new BatchResult(count, "허가정보 페이지 범위 수집 완료"));
    }

    public record BatchResult(int savedCount, String message) {}
    public record StatsResult(long totalCount) {}
}
