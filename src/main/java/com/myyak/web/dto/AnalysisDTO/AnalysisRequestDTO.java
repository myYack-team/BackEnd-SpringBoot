package com.myyak.web.dto.AnalysisDTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 약물 분석 요청 DTO
 */
public class AnalysisRequestDTO {

    /**
     * 임시 건강 메모 저장 요청
     */
    @Getter
    @NoArgsConstructor
    public static class TemporaryNoteRequest {

        @NotNull(message = "컨디션 점수는 필수 값입니다")
        @Min(value = 0, message = "컨디션 점수는 0 이상이어야 합니다")
        @Max(value = 10, message = "컨디션 점수는 10 이하여야 합니다")
        private Integer conditionScore;

        private String symptoms;  // JSON array string

        @Size(max = 500, message = "추가 메모는 500자 이내로 작성해주세요")
        private String additionalNote;
    }
}
