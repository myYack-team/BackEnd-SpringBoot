package com.myyak.web.dto.HealthNoteDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 건강 메모 응답 DTO
 */
public class HealthNoteResponseDTO {

    /**
     * 건강 메모 상세 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private Long id;
        private LocalDate noteDate;
        private Integer conditionScore;
        private String content;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 건강 메모 목록 아이템 (간략 정보)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListItem {
        private Long id;
        private LocalDate noteDate;
        private Integer conditionScore;
        private String content;
    }

    /**
     * 건강 메모 목록
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteList {
        private List<ListItem> notes;
        private Integer totalCount;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    /**
     * 생성 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResult {
        private Long id;
        private LocalDate noteDate;
        private Integer conditionScore;
        private String content;
    }

    /**
     * 수정 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private Long id;
        private LocalDate noteDate;
        private Integer conditionScore;
        private String content;
        private LocalDateTime updatedAt;
    }
}
