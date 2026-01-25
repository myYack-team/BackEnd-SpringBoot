package com.myyak.web.dto.TodayDTO;

import com.myyak.domain.enums.DrugType;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.SupplementTag;
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
        private String name;          // 원본 약 이름 (성분 포함)
        private String displayName;   // 표시용 약 이름 (성분 제외)
        private Integer dosage;
        private Boolean taken;
        private Long reminderId;      // 알림 ID (스누즈용)
        private DrugType drugType;    // 전문/일반 구분 (약물인 경우)
        private SupplementTag supplementTag;  // 영양제 태그 (영양제인 경우)
        private Boolean isSupplement; // 영양제 여부
        private String imageUrl;      // 약 이미지
        private String reminderTime;  // 개별 알림 시간 (HH:mm)
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
