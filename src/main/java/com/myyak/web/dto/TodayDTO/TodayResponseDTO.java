package com.myyak.web.dto.TodayDTO;

import com.myyak.domain.enums.MedicationTiming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class TodayResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayMedication {
        private Long id;
        private String name;
        private Integer dosage;
        private Boolean taken;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodaySchedule {
        private MedicationTiming timing;
        private String timingLabel;
        private String scheduledTime;
        private List<TodayMedication> medications;
        private Boolean allTaken;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodaySummary {
        private Integer totalMedications;
        private Integer takenCount;
        private Integer remainingCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodayResult {
        private String date;
        private String dayOfWeek;
        private List<TodaySchedule> schedules;
        private TodaySummary summary;
    }
}
