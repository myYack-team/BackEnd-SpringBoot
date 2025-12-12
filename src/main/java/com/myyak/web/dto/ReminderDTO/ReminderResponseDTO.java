package com.myyak.web.dto.ReminderDTO;

import com.myyak.domain.enums.MedicationTiming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class ReminderResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderItem {
        private Long id;
        private Long medicationId;
        private Long supplementId;  // 영양제인 경우
        private String medicationName;
        private String time;
        private MedicationTiming timing;
        private Boolean enabled;
        private LocalDateTime snoozeUntil;  // 스누즈 시간
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderList {
        private List<ReminderItem> reminders;
        private Integer totalCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private Long id;
        private String time;
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToggleResult {
        private Long id;
        private Boolean enabled;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnoozeResult {
        private Long id;
        private LocalDateTime snoozeUntil;
        private Integer snoozeMinutes;
    }
}
