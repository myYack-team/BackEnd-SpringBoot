package com.myyak.web.dto.DrugBatchDTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class DrugBatchRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class FullSyncRequest {
        private boolean includeEfficacyCrawling = false;
        private boolean includePdfImport = false;
        private String pdfFilePath;
        private boolean testMode = false;
        /**
         * 수집할 최대 레코드 수 (0 또는 null이면 전체 수집)
         */
        private Integer maxRecords;
    }
}
