package com.myyak.web.dto.UserDTO;

import com.myyak.domain.enums.FontSize;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UpdateRequest {
        private String name;
        private FontSize fontSize;
    }
}
