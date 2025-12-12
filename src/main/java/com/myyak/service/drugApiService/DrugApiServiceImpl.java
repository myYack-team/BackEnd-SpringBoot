package com.myyak.service.drugApiService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.DrugType;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.service.drugCrawlerService.DrugCrawlerService;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.myyak.util.DrugNameParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DrugApiServiceImpl implements DrugApiService {

    private final WebClient webClient;
    private final DrugInfoRepository drugInfoRepository;
    private final DrugCrawlerService drugCrawlerService;

    @Value("${api.data.go.kr.key}")
    private String apiKey;

    private static final String E_DRUG_API_URL = "http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList";
    private static final String PERMIT_API_URL = "http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PERMIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    @Transactional(readOnly = true)
    public List<EasyDrugApiResponse.EasyDrugItem> searchFromApi(String itemName) {
        String url = UriComponentsBuilder.fromHttpUrl(E_DRUG_API_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("itemName", itemName)
                .queryParam("type", "json")
                .queryParam("numOfRows", 100)
                .build()
                .toUriString();

        log.info("e약은요 API 호출: itemName={}", itemName);

        try {
            EasyDrugApiResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(EasyDrugApiResponse.class)
                    .block();

            if (response == null || response.getBody() == null || response.getBody().getItems() == null) {
                log.warn("API 응답이 비어있습니다: itemName={}", itemName);
                return Collections.emptyList();
            }

            log.info("API 응답 성공: totalCount={}", response.getBody().getTotalCount());
            return response.getBody().getItems();

        } catch (Exception e) {
            log.error("API 호출 실패: itemName={}, error={}", itemName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EasyDrugApiResponse.EasyDrugItem searchByItemSeqFromApi(String itemSeq) {
        String url = UriComponentsBuilder.fromHttpUrl(E_DRUG_API_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("itemSeq", itemSeq)
                .queryParam("type", "json")
                .build()
                .toUriString();

        log.info("e약은요 API 호출: itemSeq={}", itemSeq);

        try {
            EasyDrugApiResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(EasyDrugApiResponse.class)
                    .block();

            if (response == null || response.getBody() == null ||
                    response.getBody().getItems() == null || response.getBody().getItems().isEmpty()) {
                log.warn("API 응답이 비어있습니다: itemSeq={}", itemSeq);
                return null;
            }

            return response.getBody().getItems().get(0);

        } catch (Exception e) {
            log.error("API 호출 실패: itemSeq={}, error={}", itemSeq, e.getMessage());
            return null;
        }
    }

    @Override
    public List<DrugInfo> searchAndSave(String itemName) {
        List<EasyDrugApiResponse.EasyDrugItem> items = searchFromApi(itemName);

        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        List<DrugInfo> drugInfos = items.stream()
                .map(this::convertToDrugInfo)
                .collect(Collectors.toList());

        // 기존 데이터가 있으면 업데이트, 없으면 새로 저장
        for (DrugInfo drugInfo : drugInfos) {
            drugInfoRepository.findById(drugInfo.getItemSeq())
                    .ifPresentOrElse(
                            existing -> existing.updateFromApi(
                                    drugInfo.getItemName(),
                                    drugInfo.getDisplayName(),
                                    drugInfo.getIngredientKr(),
                                    drugInfo.getEntpName(),
                                    drugInfo.getEfficacy(),
                                    drugInfo.getUseMethod(),
                                    drugInfo.getWarning(),
                                    drugInfo.getCaution(),
                                    drugInfo.getInteraction(),
                                    drugInfo.getSideEffect(),
                                    drugInfo.getStorageMethod(),
                                    drugInfo.getImageUrl(),
                                    drugInfo.getOpenDate(),
                                    drugInfo.getApiUpdateDate()
                            ),
                            () -> drugInfoRepository.save(drugInfo)
                    );
        }

        log.info("약물 정보 저장 완료: {}건", drugInfos.size());
        return drugInfos;
    }

    @Override
    public DrugInfo searchByItemSeqAndSave(String itemSeq) {
        // 먼저 DB에서 조회
        return drugInfoRepository.findById(itemSeq)
                .orElseGet(() -> {
                    // DB에 없으면 API에서 가져와서 저장
                    EasyDrugApiResponse.EasyDrugItem item = searchByItemSeqFromApi(itemSeq);
                    if (item == null) {
                        return null;
                    }
                    DrugInfo drugInfo = convertToDrugInfo(item);
                    return drugInfoRepository.save(drugInfo);
                });
    }

    @Override
    public int fetchAllDrugs() {
        return fetchDrugsByPageRange(1, Integer.MAX_VALUE, 100);
    }

    @Override
    public int fetchDrugsByPageRange(int startPage, int endPage, int numOfRows) {
        int totalSaved = 0;
        int pageNo = startPage;

        while (pageNo <= endPage) {
            String url = UriComponentsBuilder.fromHttpUrl(E_DRUG_API_URL)
                    .queryParam("serviceKey", apiKey)
                    .queryParam("type", "json")
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .build()
                    .toUriString();

            log.info("배치 수집 중: pageNo={}", pageNo);

            try {
                EasyDrugApiResponse response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(EasyDrugApiResponse.class)
                        .block();

                if (response == null || response.getBody() == null ||
                        response.getBody().getItems() == null || response.getBody().getItems().isEmpty()) {
                    log.info("더 이상 데이터가 없습니다. 수집 종료: pageNo={}", pageNo);
                    break;
                }

                List<DrugInfo> drugInfos = response.getBody().getItems().stream()
                        .map(this::convertToDrugInfo)
                        .collect(Collectors.toList());

                drugInfoRepository.saveAll(drugInfos);
                totalSaved += drugInfos.size();

                log.info("페이지 {} 저장 완료: {}건 (누적: {}건)", pageNo, drugInfos.size(), totalSaved);

                pageNo++;

                // Rate limiting (1초 대기)
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("배치 작업 중단됨");
                break;
            } catch (Exception e) {
                log.error("배치 수집 실패: pageNo={}, error={}", pageNo, e.getMessage());
                pageNo++;
            }
        }

        log.info("배치 수집 완료: 총 {}건 저장", totalSaved);
        return totalSaved;
    }

    private DrugInfo convertToDrugInfo(EasyDrugApiResponse.EasyDrugItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfo.builder()
                .itemSeq(item.getItemSeq())
                .itemName(item.getItemName())
                .displayName(parsed.displayName())
                .ingredientKr(parsed.ingredientKr())
                .entpName(item.getEntpName())
                .efficacy(item.getEfcyQesitm())
                .useMethod(item.getUseMethodQesitm())
                .warning(item.getAtpnWarnQesitm())
                .caution(item.getAtpnQesitm())
                .interaction(item.getIntrcQesitm())
                .sideEffect(item.getSeQesitm())
                .storageMethod(item.getDepositMethodQesitm())
                .imageUrl(item.getItemImage())
                .openDate(parseDate(item.getOpenDe()))
                .apiUpdateDate(parseDate(item.getUpdateDe()))
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }

    // === 의약품 허가정보 API 메서드 ===

    @Override
    @Transactional(readOnly = true)
    public List<DrugPermitApiResponse.DrugPermitItem> searchFromPermitApi(String itemName) {
        String url = UriComponentsBuilder.fromHttpUrl(PERMIT_API_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("item_name", itemName)
                .queryParam("type", "json")
                .queryParam("numOfRows", 100)
                .build()
                .toUriString();

        log.info("허가정보 API 호출: itemName={}", itemName);

        try {
            DrugPermitApiResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(DrugPermitApiResponse.class)
                    .block();

            if (response == null || response.getBody() == null || response.getBody().getItems() == null) {
                log.warn("허가정보 API 응답이 비어있습니다: itemName={}", itemName);
                return Collections.emptyList();
            }

            log.info("허가정보 API 응답 성공: totalCount={}", response.getBody().getTotalCount());
            return response.getBody().getItems();

        } catch (Exception e) {
            log.error("허가정보 API 호출 실패: itemName={}, error={}", itemName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public int fetchAllFromPermitApi() {
        return fetchFromPermitApiByPageRange(1, Integer.MAX_VALUE, 100);
    }

    @Override
    public int fetchFromPermitApiByPageRange(int startPage, int endPage, int numOfRows) {
        int totalSaved = 0;
        int pageNo = startPage;

        while (pageNo <= endPage) {
            String url = UriComponentsBuilder.fromHttpUrl(PERMIT_API_URL)
                    .queryParam("serviceKey", apiKey)
                    .queryParam("type", "json")
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .build()
                    .toUriString();

            log.info("허가정보 배치 수집 중: pageNo={}", pageNo);

            try {
                DrugPermitApiResponse response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(DrugPermitApiResponse.class)
                        .block();

                if (response == null || response.getBody() == null ||
                        response.getBody().getItems() == null || response.getBody().getItems().isEmpty()) {
                    log.info("허가정보 더 이상 데이터가 없습니다. 수집 종료: pageNo={}", pageNo);
                    break;
                }

                List<DrugInfo> drugInfos = response.getBody().getItems().stream()
                        .map(this::convertFromPermitApi)
                        .collect(Collectors.toList());

                // 기존 데이터가 있으면 업데이트, 없으면 새로 저장
                for (DrugInfo drugInfo : drugInfos) {
                    drugInfoRepository.findById(drugInfo.getItemSeq())
                            .ifPresentOrElse(
                                    existing -> existing.updateFromPermitApi(
                                            drugInfo.getItemName(),
                                            drugInfo.getDisplayName(),
                                            drugInfo.getIngredientKr(),
                                            drugInfo.getEntpName(),
                                            drugInfo.getDrugType(),
                                            drugInfo.getIngredientName(),
                                            drugInfo.getEfficacy(),
                                            drugInfo.getUseMethod(),
                                            drugInfo.getCaution(),
                                            drugInfo.getStorageMethod(),
                                            drugInfo.getImageUrl(),
                                            drugInfo.getPermitDate()
                                    ),
                                    () -> drugInfoRepository.save(drugInfo)
                            );
                }

                totalSaved += drugInfos.size();

                log.info("허가정보 페이지 {} 저장 완료: {}건 (누적: {}건)", pageNo, drugInfos.size(), totalSaved);

                pageNo++;

                // Rate limiting (1초 대기)
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("허가정보 배치 작업 중단됨");
                break;
            } catch (Exception e) {
                log.error("허가정보 배치 수집 실패: pageNo={}, error={}", pageNo, e.getMessage());
                pageNo++;
            }
        }

        log.info("허가정보 배치 수집 완료: 총 {}건 저장", totalSaved);
        return totalSaved;
    }

    private DrugInfo convertFromPermitApi(DrugPermitApiResponse.DrugPermitItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfo.builder()
                .itemSeq(item.getItemSeq())
                .itemName(item.getItemName())
                .displayName(parsed.displayName())
                .ingredientKr(parsed.ingredientKr())
                .entpName(item.getEntpName())
                .drugType(DrugType.fromApiValue(item.getSpcltyPblc()))
                .ingredientName(item.getItemIngrName())
                .efficacy(parseDocData(item.getEeDocData()))
                .useMethod(parseDocData(item.getUdDocData()))
                .caution(parseDocData(item.getNbDocData()))
                .storageMethod(item.getStorageMethod())
                .imageUrl(item.getBigPrdtImgUrl())
                .permitDate(parsePermitDate(item.getItemPermitDate()))
                .build();
    }

    /**
     * API에서 받은 DOC_DATA를 파싱하여 텍스트 추출
     * XML 태그가 포함된 경우 태그를 제거하고 내용만 추출
     */
    private String parseDocData(String docData) {
        if (docData == null || docData.isBlank()) {
            return null;
        }

        // XML 태그 제거 (간단한 정규식 사용)
        // <![CDATA[...]]> 처리
        String result = docData.replaceAll("<!\\[CDATA\\[", "")
                               .replaceAll("\\]\\]>", "");

        // XML 태그 제거
        result = result.replaceAll("<[^>]+>", " ");

        // 연속된 공백 정리 및 trim
        result = result.replaceAll("\\s+", " ").trim();

        return result.isEmpty() ? null : result;
    }

    private LocalDate parsePermitDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, PERMIT_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("허가일자 파싱 실패: {}", dateStr);
            return null;
        }
    }

    // === 통합 배치 메서드 ===

    @Override
    public void fetchFromAllSources() {
        log.info("========== 통합 배치 시작 ==========");
        long startTime = System.currentTimeMillis();

        // 1. e약은요 API 수집
        log.info("[1/3] e약은요 API 수집 시작");
        int easyDrugCount = fetchAllDrugs();
        log.info("[1/3] e약은요 API 수집 완료: {}건", easyDrugCount);

        // 2. 허가정보 API 수집
        log.info("[2/3] 허가정보 API 수집 시작");
        int permitCount = fetchAllFromPermitApi();
        log.info("[2/3] 허가정보 API 수집 완료: {}건", permitCount);

        // 3. 크롤링으로 효능 보완
        log.info("[3/3] 의약품안전나라 크롤링 시작 (효능 보완)");
        int crawlCount = drugCrawlerService.crawlEfficacyForMissingDrugs();
        log.info("[3/3] 크롤링 완료: {}건 업데이트", crawlCount);

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;

        log.info("========== 통합 배치 완료 ==========");
        log.info("총 소요시간: {}초", duration);
        log.info("e약은요: {}건, 허가정보: {}건, 크롤링: {}건", easyDrugCount, permitCount, crawlCount);
    }
}
