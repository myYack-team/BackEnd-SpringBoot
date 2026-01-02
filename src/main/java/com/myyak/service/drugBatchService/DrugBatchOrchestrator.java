package com.myyak.service.drugBatchService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.DrugInfoTest;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.DrugInfoTestRepository;
import com.myyak.service.drugCrawlerService.DrugCrawlerService;
import com.myyak.service.drugPdfBatchService.DrugPdfBatchService;
import com.myyak.util.DrugNameParser;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 데이터 수집 배치 오케스트레이터
 * 여러 단계의 데이터 수집을 순차적으로 실행하고 상태를 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugBatchOrchestrator {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoTestRepository drugInfoTestRepository;
    private final DrugCrawlerService drugCrawlerService;
    private final DrugPdfBatchService drugPdfBatchService;
    private final WebClient webClient;

    @Value("${api.data.go.kr.key:}")
    private String apiKey;

    private static final String E_DRUG_API_URL = "http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList";
    private static final String PERMIT_API_URL = "http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PERMIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 작업 상태 저장소 (메모리)
    private final Map<String, BatchJobContext> jobContexts = new ConcurrentHashMap<>();

    /**
     * 전체 동기화 작업 시작
     */
    public BatchJobContext startFullSync(boolean testMode, boolean includeEfficacyCrawling,
                                          boolean includePdfImport, String pdfFilePath, Integer maxRecords) {
        BatchJobContext context = BatchJobContext.create(
                testMode, includeEfficacyCrawling, includePdfImport, pdfFilePath, maxRecords);

        jobContexts.put(context.getJobId(), context);

        log.info("전체 동기화 작업 시작: jobId={}, testMode={}, maxRecords={}",
                context.getJobId(), testMode, maxRecords);

        // 비동기로 실제 작업 실행
        executeFullSyncAsync(context);

        return context;
    }

    /**
     * 작업 상태 조회
     */
    public BatchJobContext getJobStatus(String jobId) {
        return jobContexts.get(jobId);
    }

    /**
     * 작업 취소 요청
     */
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

    /**
     * 비동기로 전체 동기화 실행
     */
    @Async("drugBatchExecutor")
    public void executeFullSyncAsync(BatchJobContext context) {
        try {
            log.info("========== 전체 동기화 시작 ==========");
            log.info("JobId: {}, TestMode: {}", context.getJobId(), context.isTestMode());

            // Stage 1: e약은요 API 수집
            if (!context.isCancelRequested()) {
                executeStage1(context);
            }

            // Stage 2: 허가정보 API 수집
            if (!context.isCancelRequested()) {
                executeStage2(context);
            }

            // Stage 3: 성분명 재파싱
            if (!context.isCancelRequested()) {
                executeStage3(context);
            }

            // Stage 4: 효능 크롤링 (선택)
            if (!context.isCancelRequested() && context.isIncludeEfficacyCrawling()) {
                executeStage4(context);
            }

            // Stage 5: PDF 배치 (선택)
            if (!context.isCancelRequested() && context.isIncludePdfImport()) {
                executeStage5(context);
            }

            // 완료 처리
            if (context.isCancelRequested()) {
                context.cancel();
                log.info("작업이 취소되었습니다: jobId={}", context.getJobId());
            } else {
                context.complete();
                log.info("========== 전체 동기화 완료 ==========");
            }

        } catch (Exception e) {
            log.error("전체 동기화 실패: jobId={}, error={}", context.getJobId(), e.getMessage(), e);
            context.fail(e.getMessage());
        }
    }

    /**
     * Stage 1: e약은요 API 수집
     */
    @Transactional
    protected void executeStage1(BatchJobContext context) {
        context.startStage(1);
        log.info("[Stage 1] e약은요 API 수집 시작 (maxRecords={})", context.getMaxRecords());

        int totalSaved = 0;
        int pageNo = 1;
        int numOfRows = 100;

        while (!context.isCancelRequested()) {
            // maxRecords 제한 체크
            if (context.hasMaxRecordsLimit() && totalSaved >= context.getMaxRecords()) {
                log.info("[Stage 1] maxRecords 제한 도달: {}", context.getMaxRecords());
                break;
            }
            String url = UriComponentsBuilder.fromHttpUrl(E_DRUG_API_URL)
                    .queryParam("serviceKey", apiKey)
                    .queryParam("type", "json")
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .build()
                    .toUriString();

            try {
                EasyDrugApiResponse response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(EasyDrugApiResponse.class)
                        .block();

                if (response == null || response.getBody() == null ||
                        response.getBody().getItems() == null || response.getBody().getItems().isEmpty()) {
                    log.info("[Stage 1] 더 이상 데이터가 없습니다. pageNo={}", pageNo);
                    break;
                }

                Integer totalCount = response.getBody().getTotalCount();

                if (context.isTestMode()) {
                    // 테스트 모드: drug_info_test 테이블에 저장
                    List<DrugInfoTest> testInfos = response.getBody().getItems().stream()
                            .map(this::convertFromEasyDrugApiToTest)
                            .collect(Collectors.toList());
                    drugInfoTestRepository.saveAll(testInfos);
                    totalSaved += testInfos.size();
                } else {
                    // 운영 모드: drug_info 테이블에 저장
                    List<DrugInfo> drugInfos = response.getBody().getItems().stream()
                            .map(this::convertFromEasyDrugApi)
                            .collect(Collectors.toList());
                    drugInfoRepository.saveAll(drugInfos);
                    totalSaved += drugInfos.size();
                }

                // 진행률 업데이트
                int savedCount = response.getBody().getItems().size();
                context.updateStageProgress(1, totalSaved, totalCount);
                log.debug("[Stage 1] 페이지 {} 처리 완료: {}건 (누적: {}/{})",
                        pageNo, savedCount, totalSaved, totalCount);

                pageNo++;
                Thread.sleep(1000); // Rate limiting

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("작업이 중단되었습니다", e);
            } catch (Exception e) {
                log.error("[Stage 1] 페이지 {} 처리 실패: {}", pageNo, e.getMessage());
                pageNo++;
            }
        }

        context.completeStage(1);
        log.info("[Stage 1] e약은요 API 수집 완료: {}건", totalSaved);
    }

    /**
     * Stage 2: 허가정보 API 수집
     */
    @Transactional
    protected void executeStage2(BatchJobContext context) {
        context.startStage(2);
        log.info("[Stage 2] 허가정보 API 수집 시작 (maxRecords={})", context.getMaxRecords());

        int totalSaved = 0;
        int pageNo = 1;
        int numOfRows = 100;

        while (!context.isCancelRequested()) {
            // maxRecords 제한 체크
            if (context.hasMaxRecordsLimit() && totalSaved >= context.getMaxRecords()) {
                log.info("[Stage 2] maxRecords 제한 도달: {}", context.getMaxRecords());
                break;
            }
            String url = UriComponentsBuilder.fromHttpUrl(PERMIT_API_URL)
                    .queryParam("serviceKey", apiKey)
                    .queryParam("type", "json")
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .build()
                    .toUriString();

            try {
                DrugPermitApiResponse response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(DrugPermitApiResponse.class)
                        .block();

                if (response == null || response.getBody() == null ||
                        response.getBody().getItems() == null || response.getBody().getItems().isEmpty()) {
                    log.info("[Stage 2] 더 이상 데이터가 없습니다. pageNo={}", pageNo);
                    break;
                }

                Integer totalCount = response.getBody().getTotalCount();

                for (DrugPermitApiResponse.DrugPermitItem item : response.getBody().getItems()) {
                    if (context.isTestMode()) {
                        updateFromPermitApiToTest(item);
                    } else {
                        updateFromPermitApi(item);
                    }
                }

                totalSaved += response.getBody().getItems().size();

                // 진행률 업데이트
                context.updateStageProgress(2, totalSaved, totalCount);
                log.debug("[Stage 2] 페이지 {} 처리 완료: {}건 (누적: {}/{})",
                        pageNo, response.getBody().getItems().size(), totalSaved, totalCount);

                pageNo++;
                Thread.sleep(1000); // Rate limiting

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("작업이 중단되었습니다", e);
            } catch (Exception e) {
                log.error("[Stage 2] 페이지 {} 처리 실패: {}", pageNo, e.getMessage());
                pageNo++;
            }
        }

        context.completeStage(2);
        log.info("[Stage 2] 허가정보 API 수집 완료: {}건", totalSaved);
    }

    /**
     * Stage 3: 성분명 재파싱
     */
    @Transactional
    protected void executeStage3(BatchJobContext context) {
        context.startStage(3);
        log.info("[Stage 3] 성분명 재파싱 시작");

        int updatedCount;
        if (context.isTestMode()) {
            updatedCount = executeStage3ForTest(context);
        } else {
            updatedCount = executeStage3ForProduction(context);
        }

        context.completeStage(3);
        log.info("[Stage 3] 성분명 재파싱 완료: {}건 업데이트", updatedCount);
    }

    private int executeStage3ForProduction(BatchJobContext context) {
        List<DrugInfo> drugsWithoutIngredient = drugInfoRepository.findByIngredientKrIsNull();
        int totalCount = drugsWithoutIngredient.size();
        int updatedCount = 0;

        log.info("[Stage 3] ingredientKr이 NULL인 약물 수: {}건", totalCount);

        for (int i = 0; i < drugsWithoutIngredient.size() && !context.isCancelRequested(); i++) {
            DrugInfo drug = drugsWithoutIngredient.get(i);
            DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(drug.getItemName());

            if (parsed.ingredientKr() != null) {
                drug.updateParsedNames(parsed.displayName(), parsed.ingredientKr());
                updatedCount++;
            }

            if ((i + 1) % 1000 == 0) {
                context.updateStageProgress(3, i + 1, totalCount);
            }
        }

        context.updateStageProgress(3, totalCount, totalCount);
        return updatedCount;
    }

    private int executeStage3ForTest(BatchJobContext context) {
        List<DrugInfoTest> drugsWithoutIngredient = drugInfoTestRepository.findByIngredientKrIsNull();
        int totalCount = drugsWithoutIngredient.size();
        int updatedCount = 0;

        log.info("[Stage 3] ingredientKr이 NULL인 테스트 약물 수: {}건", totalCount);

        for (int i = 0; i < drugsWithoutIngredient.size() && !context.isCancelRequested(); i++) {
            DrugInfoTest drug = drugsWithoutIngredient.get(i);
            DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(drug.getItemName());

            if (parsed.ingredientKr() != null) {
                drug.updateParsedNames(parsed.displayName(), parsed.ingredientKr());
                updatedCount++;
            }

            if ((i + 1) % 1000 == 0) {
                context.updateStageProgress(3, i + 1, totalCount);
            }
        }

        context.updateStageProgress(3, totalCount, totalCount);
        return updatedCount;
    }

    /**
     * Stage 4: 효능 크롤링 (선택)
     */
    protected void executeStage4(BatchJobContext context) {
        context.startStage(4);
        log.info("[Stage 4] 효능 크롤링 시작");

        // 기존 크롤러 서비스 활용
        int crawledCount = drugCrawlerService.crawlEfficacyForMissingDrugs();

        context.updateStageProgress(4, crawledCount, crawledCount);
        context.completeStage(4);
        log.info("[Stage 4] 효능 크롤링 완료: {}건", crawledCount);
    }

    /**
     * Stage 5: PDF 배치 (선택)
     */
    protected void executeStage5(BatchJobContext context) {
        context.startStage(5);
        log.info("[Stage 5] PDF 배치 시작: path={}", context.getPdfFilePath());

        if (context.getPdfFilePath() == null || context.getPdfFilePath().isBlank()) {
            log.warn("[Stage 5] PDF 파일 경로가 지정되지 않았습니다");
            context.completeStage(5);
            return;
        }

        DrugPdfBatchService.BatchResult result = drugPdfBatchService.importFromExcel(context.getPdfFilePath());

        context.updateStageProgress(5, result.successCount(), result.totalCount());
        context.completeStage(5);
        log.info("[Stage 5] PDF 배치 완료: {}", result.toSummary());
    }

    // === Helper Methods ===

    private DrugInfo convertFromEasyDrugApi(EasyDrugApiResponse.EasyDrugItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfo.builder()
                .itemSeq(item.getItemSeq())
                .itemName(item.getItemName())
                .displayName(parsed.displayName())
                .ingredientKr(parsed.ingredientKr())
                .entpName(item.getEntpName())
                .efficacy(item.getEfcyQesitm())
                .usage(item.getUseMethodQesitm())
                .warning(item.getAtpnWarnQesitm())
                .caution(item.getAtpnQesitm())
                .interaction(item.getIntrcQesitm())
                .sideEffect(item.getSeQesitm())
                .storageMethod(item.getDepositMethodQesitm())
                .imageUrl(item.getItemImage())
                .openDate(parseDate(item.getOpenDe(), DATE_FORMATTER))
                .apiUpdateDate(parseDate(item.getUpdateDe(), DATE_FORMATTER))
                .build();
    }

    private DrugInfoTest convertFromEasyDrugApiToTest(EasyDrugApiResponse.EasyDrugItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfoTest.builder()
                .itemSeq(item.getItemSeq())
                .itemName(item.getItemName())
                .displayName(parsed.displayName())
                .ingredientKr(parsed.ingredientKr())
                .entpName(item.getEntpName())
                .efficacy(item.getEfcyQesitm())
                .usage(item.getUseMethodQesitm())
                .warning(item.getAtpnWarnQesitm())
                .caution(item.getAtpnQesitm())
                .interaction(item.getIntrcQesitm())
                .sideEffect(item.getSeQesitm())
                .storageMethod(item.getDepositMethodQesitm())
                .imageUrl(item.getItemImage())
                .openDate(parseDate(item.getOpenDe(), DATE_FORMATTER))
                .apiUpdateDate(parseDate(item.getUpdateDe(), DATE_FORMATTER))
                .build();
    }

    private void updateFromPermitApi(DrugPermitApiResponse.DrugPermitItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        drugInfoRepository.findById(item.getItemSeq())
                .ifPresentOrElse(
                        existing -> existing.updateFromPermitApi(
                                item.getItemName(),
                                parsed.displayName(),
                                parsed.ingredientKr(),
                                item.getEntpName(),
                                com.myyak.domain.enums.DrugType.fromApiValue(item.getSpcltyPblc()),
                                item.getItemIngrName(),
                                parseDocData(item.getEeDocData()),
                                parseDocData(item.getUdDocData()),
                                parseDocData(item.getNbDocData()),
                                item.getStorageMethod(),
                                item.getBigPrdtImgUrl(),
                                parseDate(item.getItemPermitDate(), PERMIT_DATE_FORMATTER)
                        ),
                        () -> {
                            // 기존에 없는 경우 새로 저장
                            DrugInfo newDrug = DrugInfo.builder()
                                    .itemSeq(item.getItemSeq())
                                    .itemName(item.getItemName())
                                    .displayName(parsed.displayName())
                                    .ingredientKr(parsed.ingredientKr())
                                    .entpName(item.getEntpName())
                                    .drugType(com.myyak.domain.enums.DrugType.fromApiValue(item.getSpcltyPblc()))
                                    .ingredientName(item.getItemIngrName())
                                    .efficacy(parseDocData(item.getEeDocData()))
                                    .usage(parseDocData(item.getUdDocData()))
                                    .caution(parseDocData(item.getNbDocData()))
                                    .storageMethod(item.getStorageMethod())
                                    .imageUrl(item.getBigPrdtImgUrl())
                                    .permitDate(parseDate(item.getItemPermitDate(), PERMIT_DATE_FORMATTER))
                                    .build();
                            drugInfoRepository.save(newDrug);
                        }
                );
    }

    private void updateFromPermitApiToTest(DrugPermitApiResponse.DrugPermitItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        drugInfoTestRepository.findById(item.getItemSeq())
                .ifPresentOrElse(
                        existing -> existing.updateFromPermitApi(
                                item.getItemName(),
                                parsed.displayName(),
                                parsed.ingredientKr(),
                                item.getEntpName(),
                                com.myyak.domain.enums.DrugType.fromApiValue(item.getSpcltyPblc()),
                                item.getItemIngrName(),
                                parseDocData(item.getEeDocData()),
                                parseDocData(item.getUdDocData()),
                                parseDocData(item.getNbDocData()),
                                item.getStorageMethod(),
                                item.getBigPrdtImgUrl(),
                                parseDate(item.getItemPermitDate(), PERMIT_DATE_FORMATTER)
                        ),
                        () -> {
                            // 테스트 테이블에 새로 저장
                            DrugInfoTest newDrug = DrugInfoTest.builder()
                                    .itemSeq(item.getItemSeq())
                                    .itemName(item.getItemName())
                                    .displayName(parsed.displayName())
                                    .ingredientKr(parsed.ingredientKr())
                                    .entpName(item.getEntpName())
                                    .drugType(com.myyak.domain.enums.DrugType.fromApiValue(item.getSpcltyPblc()))
                                    .ingredientName(item.getItemIngrName())
                                    .efficacy(parseDocData(item.getEeDocData()))
                                    .usage(parseDocData(item.getUdDocData()))
                                    .caution(parseDocData(item.getNbDocData()))
                                    .storageMethod(item.getStorageMethod())
                                    .imageUrl(item.getBigPrdtImgUrl())
                                    .permitDate(parseDate(item.getItemPermitDate(), PERMIT_DATE_FORMATTER))
                                    .build();
                            drugInfoTestRepository.save(newDrug);
                        }
                );
    }

    private String parseDocData(String docData) {
        if (docData == null || docData.isBlank()) {
            return null;
        }

        String result = docData.replaceAll("<!\\[CDATA\\[", "")
                               .replaceAll("\\]\\]>", "");
        result = result.replaceAll("<[^>]+>", " ");
        result = result.replaceAll("\\s+", " ").trim();

        return result.isEmpty() ? null : result;
    }

    private LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패: {}", dateStr);
            return null;
        }
    }
}
