package com.myyak.web.dto.ReminderDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ReminderRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateTimeRequest {
        @NotBlank(message = "시간은 필수입니다")
        @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "시간 형식은 HH:mm이어야 합니다")
        private String time;
    }
}
