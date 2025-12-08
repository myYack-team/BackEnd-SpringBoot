package com.myyak.service.userService;

import com.myyak.domain.User;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;

public interface UserService {

    User findById(Long userId);

    UserResponseDTO.UserInfo getMyInfo(Long userId);

    UserResponseDTO.UpdateResult updateMyInfo(Long userId, UserRequestDTO.UpdateRequest request);

    // 테스트용 사용자 생성
    UserResponseDTO.UserInfo createTestUser(String name);
}
