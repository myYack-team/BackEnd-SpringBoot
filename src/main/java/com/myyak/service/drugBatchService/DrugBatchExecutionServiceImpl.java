package com.myyak.service.drugBatchService;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.DrugInfoTest;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.DrugInfoTestRepository;
import com.myyak.service.drugApiService.DrugInfoPersistenceService;
import com.myyak.service.drugPdfBatchService.DrugPdfBatchService;
import com.myyak.util.DrugNameParser;
import com.myyak.web.dto.DrugApiDTO.DrugPermitApiResponse;
import com.myyak.web.dto.DrugApiDTO.EasyDrugApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 전체 동기화 실제 실행 서비스 구현체
 *
 * [트랜잭션 경계]
 * Stage 1~2는 수천 페이지를 수집하며 페이지마다 API 호출 + 1초 대기가 발생하므로,
 * 스테이지 전체를 하나의 트랜잭션으로 묶지 않고 페이지 단위 저장만
 * DrugInfoPersistenceService의 별도 트랜잭션으로 처리합니다 (커넥션 장시간 점유 방지).
 * Stage 3은 순수 DB 작업이므로 TransactionTemplate으로 하나의 트랜잭션에서 실행합니다
 * (더티 체킹 기반 업데이트가 실제로 반영되도록 보장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugBatchExecutionServiceImpl implements DrugBatchExecutionService {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugInfoTestRepository drugInfoTestRepository;
    private final DrugInfoPersistenceService drugInfoPersistenceService;
    private final DrugPdfBatchService drugPdfBatchService;
    private final WebClient webClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${api.data.go.kr.key:}")
    private String apiKey;

    private static final String E_DRUG_API_URL = "http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList";
    private static final String PERMIT_API_URL = "http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PERMIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 공공데이터 API 레이트리밋 대기 시간 (ms) */
    private static final long API_RATE_LIMIT_DELAY_MS = 1000;

    /**
     * 비동기로 전체 동기화 실행
     */
    @Override
    @Async("drugBatchExecutor")
    public void executeFullSync(BatchJobContext context) {
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

            // Stage 4: PDF 배치 (CSV 파일에서 약물 정보 보강)
            if (!context.isCancelRequested()) {
                executeStage4_PdfBatch(context);
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
     * 페이지 단위 저장은 DrugInfoPersistenceService의 별도 트랜잭션으로 처리
     */
    private void executeStage1(BatchJobContext context) {
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
                    totalSaved += drugInfoPersistenceService.saveEasyDrugTestPage(testInfos);
                } else {
                    // 운영 모드: drug_info 테이블에 저장
                    List<DrugInfo> drugInfos = response.getBody().getItems().stream()
                            .map(this::convertFromEasyDrugApi)
                            .collect(Collectors.toList());
                    totalSaved += drugInfoPersistenceService.saveEasyDrugPage(drugInfos);
                }

                // 진행률 업데이트
                int savedCount = response.getBody().getItems().size();
                context.updateStageProgress(1, totalSaved, totalCount);
                log.debug("[Stage 1] 페이지 {} 처리 완료: {}건 (누적: {}/{})",
                        pageNo, savedCount, totalSaved, totalCount);

                pageNo++;
                Thread.sleep(API_RATE_LIMIT_DELAY_MS); // Rate limiting

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
     * 페이지 단위 저장은 DrugInfoPersistenceService의 별도 트랜잭션으로 처리
     */
    private void executeStage2(BatchJobContext context) {
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

                // 기존 데이터가 있으면 업데이트, 없으면 새로 저장 (itemSeq 목록 1회 조회)
                if (context.isTestMode()) {
                    List<DrugInfoTest> testInfos = response.getBody().getItems().stream()
                            .map(this::convertFromPermitApiToTest)
                            .collect(Collectors.toList());
                    totalSaved += drugInfoPersistenceService.savePermitDrugTestPage(testInfos);
                } else {
                    List<DrugInfo> drugInfos = response.getBody().getItems().stream()
                            .map(this::convertFromPermitApi)
                            .collect(Collectors.toList());
                    totalSaved += drugInfoPersistenceService.savePermitDrugPage(drugInfos);
                }

                // 진행률 업데이트
                context.updateStageProgress(2, totalSaved, totalCount);
                log.debug("[Stage 2] 페이지 {} 처리 완료: {}건 (누적: {}/{})",
                        pageNo, response.getBody().getItems().size(), totalSaved, totalCount);

                pageNo++;
                Thread.sleep(API_RATE_LIMIT_DELAY_MS); // Rate limiting

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
     * 더티 체킹 기반 업데이트이므로 TransactionTemplate으로 트랜잭션 경계를 보장
     * (순수 DB 작업으로 API 대기가 없어 단일 트랜잭션으로 처리)
     */
    private void executeStage3(BatchJobContext context) {
        context.startStage(3);
        log.info("[Stage 3] 성분명 재파싱 시작");

        Integer updatedCount = transactionTemplate.execute(status -> {
            if (context.isTestMode()) {
                return executeStage3ForTest(context);
            }
            return executeStage3ForProduction(context);
        });

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
     * Stage 4: PDF 배치 (CSV 파일에서 약물 정보 보강)
     * - 효능효과, 용법용량, 주의사항, 저장방법, 유효기간 등
     */
    private void executeStage4_PdfBatch(BatchJobContext context) {
        context.startStage(4);

        // CSV 파일이 없으면 스킵
        if (!context.hasCsvFile()) {
            log.warn("[Stage 4] CSV 파일이 지정되지 않았습니다. PDF 배치를 건너뜁니다.");
            context.completeStage(4);
            return;
        }

        log.info("[Stage 4] PDF 배치 시작: fileName={}", context.getCsvFileName());
        DrugPdfBatchService.BatchResult result = drugPdfBatchService.importFromCsv(
                context.getCsvInputStream(), context.getCsvFileName());

        context.updateStageProgress(4, result.successCount(), result.totalCount());
        context.completeStage(4);
        log.info("[Stage 4] PDF 배치 완료: {}", result.toSummary());
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

    private DrugInfo convertFromPermitApi(DrugPermitApiResponse.DrugPermitItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfo.builder()
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
    }

    private DrugInfoTest convertFromPermitApiToTest(DrugPermitApiResponse.DrugPermitItem item) {
        DrugNameParser.ParsedDrugName parsed = DrugNameParser.parse(item.getItemName());

        return DrugInfoTest.builder()
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
