package com.myyak.web.dto.IntakeDTO;

import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class IntakeResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntakeItem {
        private Long id;
        private Long medicationId;
        private String medicationName;
        private LocalDateTime takenAt;
        private IntakeStatus status;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicationStockInfo {
        private Long id;
        private Integer remainingCount;
        private Boolean lowStock;
        private String lowStockMessage;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResult {
        private List<IntakeItem> intakes;
        private List<MedicationStockInfo> updatedMedications;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleMedication {
        private Long id;
        private String name;
        private String displayName;      // 표시용 약물명 (한글 이름만)
        private String ingredientKr;     // 한글 성분명
        private Integer dosage;
        private Boolean taken;
        private LocalDateTime takenAt;
        private String imageUrl;         // 약 이미지
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleItem {
        private MedicationTiming timing;
        private String timingLabel;
        private String scheduledTime;
        private List<ScheduleMedication> medications;
        private Boolean allTaken;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySummary {
        private Integer totalScheduled;
        private Integer totalTaken;
        private Double completionRate;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyIntakeResult {
        private String date;
        private List<ScheduleItem> schedules;
        private DailySummary summary;
    }

    // 복용 달력 데이터용 DTO
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaySummaryItem {
        private String date;
        private Integer totalScheduled;
        private Integer totalTaken;
        private String status;  // COMPLETE, PARTIAL, MISSED, PENDING, NONE
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlySummaryResult {
        private Integer year;
        private Integer month;
        private List<DaySummaryItem> days;
    }
}
