package com.myyak.service.userService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.UserConverter;
import com.myyak.domain.User;
import com.myyak.repository.UserRepository;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
    }

    @Override
    public UserResponseDTO.UserInfo getMyInfo(Long userId) {
        User user = findById(userId);
        return UserConverter.toUserInfo(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.UpdateResult updateMyInfo(Long userId, UserRequestDTO.UpdateRequest request) {
        User user = findById(userId);

        if (request.getName() != null) {
            user.updateProfile(request.getName(), user.getProfileImage());
        }
        if (request.getFontSize() != null) {
            user.updateFontSize(request.getFontSize());
        }

        return UserConverter.toUpdateResult(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.UserInfo createTestUser(String name) {
        User user = User.builder()
                .kakaoId("test_" + System.currentTimeMillis())
                .name(name)
                .build();
        userRepository.save(user);
        return UserConverter.toUserInfo(user);
    }
}
