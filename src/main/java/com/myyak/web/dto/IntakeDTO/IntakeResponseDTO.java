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
        private Integer dosage;
        private Boolean taken;
        private LocalDateTime takenAt;
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
}
