package com.myyak.service.excelReaderService;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExcelReaderServiceImpl implements ExcelReaderService {

    // 컬럼 인덱스 (0부터 시작, A=0)
    private static final int COL_ITEM_SEQ = 0;         // A컬럼: 품목일련번호
    private static final int COL_EFFICACY_URL = 16;    // Q컬럼: 전문의약품설명서 PDF URL
    private static final int COL_USAGE_URL = 17;       // R컬럼: 일반의약품설명서 PDF URL
    private static final int COL_PRECAUTION_URL = 18;  // S컬럼: 일반의약품용법용량 PDF URL
    private static final int COL_PRODUCT_IMAGE = 19;   // T컬럼: 제품안내사항 이미지 URL
    private static final int COL_STORAGE_METHOD = 20;  // U컬럼: 보관방법 TEXT

    @Override
    public List<DrugPdfData> readDrugPdfData(String filePath) {
        List<DrugPdfData> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new HSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();

            log.info("Excel 파일 읽기 시작: {} (총 {}행)", filePath, totalRows);

            // 첫 번째 행은 헤더이므로 건너뜀
            for (int i = 1; i < totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    DrugPdfData data = parseRow(row);
                    if (data != null && data.getItemSeq() != null && !data.getItemSeq().isBlank()) {
                        result.add(data);
                    }
                } catch (Exception e) {
                    log.warn("행 {} 파싱 실패: {}", i + 1, e.getMessage());
                }

                if (i % 10000 == 0) {
                    log.info("Excel 읽기 진행 중: {}/{}", i, totalRows);
                }
            }

            log.info("Excel 파일 읽기 완료: {}건", result.size());

        } catch (IOException e) {
            log.error("Excel 파일 읽기 실패: {}", e.getMessage());
            throw new RuntimeException("Excel 파일 읽기 실패: " + filePath, e);
        }

        return result;
    }

    private DrugPdfData parseRow(Row row) {
        String itemSeq = getCellValueAsString(row.getCell(COL_ITEM_SEQ));
        if (itemSeq == null || itemSeq.isBlank()) {
            return null;
        }

        return DrugPdfData.builder()
                .itemSeq(itemSeq.trim())
                .efficacyUrl(getCellValueAsString(row.getCell(COL_EFFICACY_URL)))
                .usageUrl(getCellValueAsString(row.getCell(COL_USAGE_URL)))
                .precautionUrl(getCellValueAsString(row.getCell(COL_PRECAUTION_URL)))
                .productImageUrl(getCellValueAsString(row.getCell(COL_PRODUCT_IMAGE)))
                .storageMethod(getCellValueAsString(row.getCell(COL_STORAGE_METHOD)))
                .build();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }
}
