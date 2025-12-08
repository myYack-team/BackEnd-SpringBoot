package com.myyak.converter;

import com.myyak.domain.User;
import com.myyak.web.dto.UserDTO.UserResponseDTO;

public class UserConverter {

    public static UserResponseDTO.UserInfo toUserInfo(User user) {
        return UserResponseDTO.UserInfo.builder()
                .id(user.getId())
                .kakaoId(user.getKakaoId())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .fontSize(user.getFontSize())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserResponseDTO.UpdateResult toUpdateResult(User user) {
        return UserResponseDTO.UpdateResult.builder()
                .id(user.getId())
                .name(user.getName())
                .fontSize(user.getFontSize())
                .build();
    }
}
