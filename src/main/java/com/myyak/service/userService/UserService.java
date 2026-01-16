package com.myyak.service.userService;

import com.myyak.domain.User;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;

public interface UserService {

    User findById(Long userId);

    UserResponseDTO.UserInfo getMyInfo(Long userId);

    UserResponseDTO.UpdateResult updateMyInfo(Long userId, UserRequestDTO.UpdateRequest request);

    /**
     * 회원 탈퇴 (Cascade 삭제)
     * 사용자의 모든 관련 데이터를 삭제합니다.
     */
    void deleteMe(Long userId);

    /**
     * 알림 설정 조회
     */
    UserResponseDTO.NotificationSettings getNotificationSettings(Long userId);

    /**
     * 알림 설정 수정
     */
    UserResponseDTO.NotificationSettings updateNotificationSettings(Long userId, UserRequestDTO.UpdateNotificationSettings request);

    // 테스트용 사용자 생성
    UserResponseDTO.UserInfo createTestUser(String name);

    /**
     * 신규 가입자 기본정보 설정
     */
    UserResponseDTO.ProfileSetupResult setupProfile(Long userId, UserRequestDTO.ProfileSetupRequest request);

    /**
     * AI 데이터 동의 상태 조회
     */
    UserResponseDTO.AiConsentStatus getAiConsentStatus(Long userId);

    /**
     * AI 데이터 동의 상태 변경
     */
    UserResponseDTO.AiConsentStatus updateAiConsent(Long userId, UserRequestDTO.UpdateAiConsentRequest request);
}
