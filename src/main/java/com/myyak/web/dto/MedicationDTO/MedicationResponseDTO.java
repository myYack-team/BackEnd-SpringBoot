package com.myyak.web.dto.MedicationDTO;

import com.myyak.domain.enums.MedicationTiming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MedicationResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResult {
        private Long id;
        private String drugName;         // 약 이름 (DrugInfo 또는 customDrugName)
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private Integer durationDays;
        private Integer totalCount;
        private Integer remainingCount;
        private LocalDate startDate;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationListItem {
        private Long id;
        private String drugName;         // 약 이름
        private String displayName;      // 표시용 약물명 (한글 이름만)
        private String ingredientKr;     // 한글 성분명
        private String imageUrl;         // 약 이미지 (DrugInfo에서)
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private Integer remainingCount;
        private Integer daysLeft;
        private List<ReminderInfo> reminders;  // 알림 정보 (시간 표시용)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationList {
        private List<MedicationListItem> medications;
        private Integer totalCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationDetail {
        private Long id;
        private String drugName;         // 약 이름
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private Integer durationDays;
        private Integer totalCount;
        private Integer remainingCount;
        private LocalDate startDate;
        private LocalDateTime createdAt;
        private String memo;             // 사용자 메모
        private List<ReminderInfo> reminders;

        // DrugInfo에서 가져온 약 정보
        private DrugInfoData drugInfo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrugInfoData {
        private String itemSeq;
        private String itemName;
        private String displayName;      // 표시용 약물명 (한글 이름만)
        private String ingredientKr;     // 한글 성분명
        private String entpName;         // 제약회사
        private String efficacy;         // 효능/효과
        private String useMethod;        // 용법/용량
        private String warning;          // 주의사항 경고
        private String caution;          // 주의사항
        private String interaction;      // 상호작용
        private String sideEffect;       // 부작용
        private String storageMethod;    // 보관법
        private String imageUrl;         // 약 이미지
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderInfo {
        private Long id;
        private String time;
        private MedicationTiming timing;
        private String timingLabel;      // "아침 식후" 등
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private Long id;
        private String drugName;
        private Integer dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchDeleteResult {
        private int requestedCount;
        private int deletedCount;
    }
}
