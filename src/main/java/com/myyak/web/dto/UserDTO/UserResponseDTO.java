package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class UserResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String kakaoId;
        private String name;
        private String profileImage;
        private FontSize fontSize;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private Long id;
        private String name;
        private FontSize fontSize;
    }
}
