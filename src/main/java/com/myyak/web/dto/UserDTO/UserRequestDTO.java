package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String name;
        private FontSize fontSize;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateNotificationSettings {
        @NotNull(message = "알림 설정 값은 필수입니다")
        private Boolean notificationEnabled;
    }
}
