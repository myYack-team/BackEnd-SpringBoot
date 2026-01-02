package com.myyak.service.excelReaderService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExcelReaderServiceImpl implements ExcelReaderService {

    // CSV 컬럼명 (OpenData_ItemPermitDetail CSV 파일 구조)
    private static final String COL_ITEM_SEQ = "품목일련번호";
    private static final String COL_EFFICACY = "효능효과";      // EE PDF URL
    private static final String COL_USAGE = "용법용량";         // UD PDF URL
    private static final String COL_PRECAUTION = "주의사항";    // NB PDF URL
    private static final String COL_ATTACHMENT = "첨부문서";    // II PDF URL (일부만 있음)
    private static final String COL_STORAGE = "저장방법";
    private static final String COL_EXPIRY = "유효기간";

    @Override
    public List<DrugPdfData> readDrugPdfData(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return readDrugPdfData(fis, filePath);
        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패: {}", e.getMessage());
            throw new RuntimeException("CSV 파일 읽기 실패: " + filePath, e);
        }
    }

    @Override
    public List<DrugPdfData> readDrugPdfData(InputStream inputStream, String fileName) {
        List<DrugPdfData> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            // 첫 번째 행은 헤더
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("CSV 파일이 비어있습니다: " + fileName);
            }

            // BOM 제거
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            // 헤더 파싱하여 컬럼 인덱스 매핑
            Map<String, Integer> columnIndex = parseHeader(headerLine);
            log.info("CSV 파일 읽기 시작: {} (컬럼 수: {})", fileName, columnIndex.size());
            log.info("컬럼 매핑: {}", columnIndex);

            // 필수 컬럼 확인
            if (!columnIndex.containsKey(COL_ITEM_SEQ)) {
                throw new RuntimeException("필수 컬럼 없음: " + COL_ITEM_SEQ);
            }

            // 데이터 행 읽기
            String line;
            int lineNumber = 1;
            int successCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    DrugPdfData data = parseCsvLine(line, columnIndex);
                    if (data != null && data.getItemSeq() != null && !data.getItemSeq().isBlank()) {
                        result.add(data);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("행 {} 파싱 실패: {}", lineNumber, e.getMessage());
                }

                if (lineNumber % 10000 == 0) {
                    log.info("CSV 읽기 진행 중: {} 행 처리됨 (성공: {})", lineNumber, successCount);
                }
            }

            log.info("CSV 파일 읽기 완료: 총 {}행, 성공 {}건", lineNumber, result.size());

        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패: {}", e.getMessage());
            throw new RuntimeException("CSV 파일 읽기 실패: " + fileName, e);
        }

        return result;
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> columnIndex = new HashMap<>();
        String[] headers = parseCsvRow(headerLine);

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim();
            columnIndex.put(header, i);
        }

        return columnIndex;
    }

    private DrugPdfData parseCsvLine(String line, Map<String, Integer> columnIndex) {
        String[] values = parseCsvRow(line);

        String itemSeq = getValueByColumn(values, columnIndex, COL_ITEM_SEQ);
        if (itemSeq == null || itemSeq.isBlank()) {
            return null;
        }

        // PDF URL 추출
        String efficacyUrl = getValueByColumn(values, columnIndex, COL_EFFICACY);
        String usageUrl = getValueByColumn(values, columnIndex, COL_USAGE);
        String precautionUrl = getValueByColumn(values, columnIndex, COL_PRECAUTION);
        String attachmentUrl = getValueByColumn(values, columnIndex, COL_ATTACHMENT);
        String storageMethod = getValueByColumn(values, columnIndex, COL_STORAGE);
        String expiry = getValueByColumn(values, columnIndex, COL_EXPIRY);

        // 저장방법과 유효기간 합치기
        String storage = storageMethod;
        if (expiry != null && !expiry.isBlank()) {
            storage = (storage != null ? storage + ", " : "") + expiry;
        }

        return DrugPdfData.builder()
                .itemSeq(itemSeq.trim())
                .efficacyUrl(efficacyUrl)
                .usageUrl(usageUrl)
                .precautionUrl(precautionUrl)
                .productImageUrl(attachmentUrl)  // 첨부문서 URL을 productImage로 사용
                .storageMethod(storage)
                .build();
    }

    private String getValueByColumn(String[] values, Map<String, Integer> columnIndex, String columnName) {
        Integer index = columnIndex.get(columnName);
        if (index == null || index >= values.length) {
            return null;
        }
        String value = values[index].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * CSV 행을 파싱 (쌍따옴표 내 쉼표 처리)
     */
    private String[] parseCsvRow(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 이스케이프된 쌍따옴표
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }
}
