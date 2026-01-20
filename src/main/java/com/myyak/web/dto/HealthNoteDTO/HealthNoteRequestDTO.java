package com.myyak.web.dto.HealthNoteDTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 건강 메모 요청 DTO
 */
public class HealthNoteRequestDTO {

    /**
     * 건강 메모 생성 요청
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "날짜는 필수입니다")
        private LocalDate noteDate;

        @Min(value = 0, message = "컨디션 점수는 0 이상이어야 합니다")
        @Max(value = 10, message = "컨디션 점수는 10 이하여야 합니다")
        private Integer conditionScore = 10;

        @Size(max = 500, message = "메모 내용은 500자 이내여야 합니다")
        private String content;
    }

    /**
     * 건강 메모 수정 요청
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {

        @Min(value = 0, message = "컨디션 점수는 0 이상이어야 합니다")
        @Max(value = 10, message = "컨디션 점수는 10 이하여야 합니다")
        private Integer conditionScore;

        @Size(max = 500, message = "메모 내용은 500자 이내여야 합니다")
        private String content;
    }
}
