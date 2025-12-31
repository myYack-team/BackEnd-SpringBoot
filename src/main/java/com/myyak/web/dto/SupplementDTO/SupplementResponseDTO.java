package com.myyak.web.dto.SupplementDTO;

import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.SupplementTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SupplementResponseDTO {

    /**
     * 영양제 마스터 목록 아이템
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementListItem {
        private Long id;
        private String name;
        private String description;
        private SupplementTag tag;
        private String tagLabel;  // "비타민 C" 등
        private String imageUrl;
        private Integer selectionCount;  // 인기도
        private String createdByName;  // 등록자 닉네임
    }

    /**
     * 영양제 마스터 목록 (페이징)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementList {
        private List<SupplementListItem> supplements;
        private Integer totalCount;
        private Integer page;
        private Integer size;
        private Integer totalPages;
        private Boolean hasNext;
    }

    /**
     * 영양제 마스터 상세
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementDetail {
        private Long id;
        private String name;
        private String description;
        private SupplementTag tag;
        private String tagLabel;
        private String imageUrl;
        private Integer selectionCount;
        private String createdByName;
        private Long createdById;
        private LocalDateTime createdAt;
    }

    /**
     * 영양제 마스터 생성 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSupplementResult {
        private Long id;
        private String name;
        private SupplementTag tag;
        private String tagLabel;
        private LocalDateTime createdAt;
    }

    /**
     * 사용자 영양제 목록 아이템
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSupplementListItem {
        private Long id;
        private Long supplementId;
        private String supplementName;
        private SupplementTag tag;
        private String tagLabel;
        private String imageUrl;
        private String dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
    }

    /**
     * 사용자 영양제 목록
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSupplementList {
        private List<UserSupplementListItem> supplements;
        private Integer totalCount;
    }

    /**
     * 사용자 영양제 상세
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSupplementDetail {
        private Long id;
        private String dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private LocalDate startDate;
        private LocalDate endDate;
        private String memo;
        private LocalDateTime createdAt;
        private List<ReminderInfo> reminders;

        // 영양제 마스터 정보
        private SupplementInfo supplementInfo;
    }

    /**
     * 영양제 마스터 정보 (상세 조회용)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplementInfo {
        private Long id;
        private String name;
        private String description;
        private SupplementTag tag;
        private String tagLabel;
        private String imageUrl;
        private Integer selectionCount;
        private String createdByName;
    }

    /**
     * 알림 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderInfo {
        private Long id;
        private String time;
        private MedicationTiming timing;
        private String timingLabel;
        private Boolean enabled;
    }

    /**
     * 사용자 영양제 생성 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddUserSupplementResult {
        private Long id;
        private String supplementName;
        private String dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private LocalDateTime createdAt;
    }

    /**
     * 사용자 영양제 수정 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserSupplementResult {
        private Long id;
        private String supplementName;
        private String dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
    }

    /**
     * 사용자 영양제 일괄 삭제 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchDeleteResult {
        private int requestedCount;
        private int deletedCount;
        private int failedCount;
    }
}
