package com.myyak.service.drugCrawlerService;

import com.myyak.domain.DrugInfo;
import com.myyak.repository.DrugInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * 의약품안전나라 크롤링 서비스 구현체
 * 효능 정보가 없는 의약품에 대해 크롤링으로 데이터 보완
 */
@Slf4j
@Service
public class DrugCrawlerServiceImpl implements DrugCrawlerService {

    private final DrugInfoRepository drugInfoRepository;
    private final DrugCrawlerServiceImpl self; // 자기 자신 주입 (프록시 호출용)

    public DrugCrawlerServiceImpl(DrugInfoRepository drugInfoRepository,
                                   @Lazy DrugCrawlerServiceImpl self) {
        this.drugInfoRepository = drugInfoRepository;
        this.self = self;
    }

    private static final String NEDRUG_DETAIL_URL = "https://nedrug.mfds.go.kr/pbp/CCBBB01/getItemDetail";
    private static final int REQUEST_DELAY_MS = 1000; // 1초 딜레이
    private static final int TIMEOUT_MS = 10000; // 10초 타임아웃

    @Override
    @Transactional(readOnly = true)
    public int crawlEfficacyForMissingDrugs() {
        List<DrugInfo> drugsWithoutEfficacy = drugInfoRepository.findByEfficacyIsNullOrEmpty();
        log.info("효능 정보가 없는 약물 수: {}건", drugsWithoutEfficacy.size());

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < drugsWithoutEfficacy.size(); i++) {
            DrugInfo drug = drugsWithoutEfficacy.get(i);

            try {
                // self를 통해 호출해야 프록시가 적용되어 REQUIRES_NEW 트랜잭션이 동작
                boolean success = self.crawlAndSaveEfficacy(drug.getItemSeq());
                if (success) {
                    successCount++;
                    log.info("[{}/{}] 크롤링 성공: {} ({})",
                            i + 1, drugsWithoutEfficacy.size(),
                            drug.getDisplayName(), drug.getItemSeq());
                } else {
                    failCount++;
                    log.warn("[{}/{}] 크롤링 실패 (데이터 없음): {} ({})",
                            i + 1, drugsWithoutEfficacy.size(),
                            drug.getDisplayName(), drug.getItemSeq());
                }

                // Rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("크롤링 작업 중단됨");
                break;
            } catch (Exception e) {
                failCount++;
                log.error("[{}/{}] 크롤링 오류: {} ({}) - {}",
                        i + 1, drugsWithoutEfficacy.size(),
                        drug.getDisplayName(), drug.getItemSeq(), e.getMessage());
            }
        }

        log.info("크롤링 완료: 성공 {}건, 실패 {}건", successCount, failCount);
        return successCount;
    }

    @Override
    public boolean crawlEfficacyByItemSeq(String itemSeq) {
        return self.crawlAndSaveEfficacy(itemSeq);
    }

    /**
     * 개별 약물 효능 크롤링 및 저장 (독립 트랜잭션)
     * REQUIRES_NEW로 각 건마다 즉시 커밋되어 중간에 중단되어도 이전 데이터는 유지됨
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean crawlAndSaveEfficacy(String itemSeq) {
        try {
            String url = NEDRUG_DETAIL_URL + "?itemSeq=" + itemSeq;

            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            // 효능효과 섹션 찾기: <div class="info_box" id="_ee_doc">
            Element efficacyDiv = doc.getElementById("_ee_doc");

            if (efficacyDiv == null || efficacyDiv.text().isBlank()) {
                log.debug("효능 정보를 찾을 수 없음: itemSeq={}", itemSeq);
                return false;
            }

            String efficacy = cleanEfficacyText(efficacyDiv.text());

            if (efficacy.isEmpty()) {
                return false;
            }

            // DB 업데이트
            DrugInfo drug = drugInfoRepository.findById(itemSeq).orElse(null);
            if (drug != null) {
                drug.updateEfficacy(efficacy);
                drugInfoRepository.save(drug);
                log.debug("효능 정보 업데이트 완료: itemSeq={}, efficacy={}", itemSeq, truncate(efficacy, 50));
                return true;
            }

            return false;

        } catch (IOException e) {
            log.error("크롤링 실패: itemSeq={}, error={}", itemSeq, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countDrugsWithoutEfficacy() {
        return drugInfoRepository.countByEfficacyIsNullOrEmpty();
    }

    /**
     * 효능 텍스트 정리
     * - 불필요한 공백 제거
     * - HTML 엔티티 변환
     */
    private String cleanEfficacyText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("\\s+", " ")  // 연속 공백을 하나로
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .trim();
    }

    /**
     * 문자열 자르기 (로그용)
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}
