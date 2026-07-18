package com.myyak.service.userService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.UserConverter;
import com.myyak.domain.User;
import com.myyak.repository.*;
import com.myyak.service.oAuthService.kakaoService.KakaoOAuthService;
import com.myyak.util.PhoneHashUtil;
import com.myyak.web.dto.UserDTO.UserRequestDTO;
import com.myyak.web.dto.UserDTO.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final UserSupplementRepository userSupplementRepository;
    private final IntakeRepository intakeRepository;
    private final ReminderRepository reminderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final AnalysisTemporaryNoteRepository analysisTemporaryNoteRepository;
    private final UserAnalysisQuotaRepository userAnalysisQuotaRepository;
    private final HealthNoteRepository healthNoteRepository;
    private final QnAReplyRepository qnAReplyRepository;
    private final QnARepository qnARepository;
    private final FamilyLinkRepository familyLinkRepository;
    private final FamilyLinkRequestRepository familyLinkRequestRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final KakaoOAuthService kakaoOAuthService;
    private final PhoneHashUtil phoneHashUtil;

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
            String trimmedName = request.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new GeneralException(ErrorStatus.INVALID_NICKNAME);
            }
            // 본인 ID를 제외하고 중복 검사 (대소문자 collation 고려)
            if (userRepository.existsByNameAndIdNot(trimmedName, userId)) {
                throw new GeneralException(ErrorStatus.NICKNAME_ALREADY_EXISTS);
            }
            user.updateProfile(trimmedName, user.getProfileImage());
        }
        if (request.getFontSize() != null) {
            user.updateFontSize(request.getFontSize());
        }

        return UserConverter.toUpdateResult(user);
    }

    @Override
    @Transactional
    public void deleteMe(Long userId) {
        User user = findById(userId);
        log.info("회원 탈퇴 시작 - userId: {}, name: {}", userId, user.getName());

        // 0. 카카오 연결 끊기 (Admin API 사용)
        String kakaoId = user.getKakaoId();
        if (kakaoId != null && !kakaoId.isBlank() && !kakaoId.startsWith("test_")) {
            kakaoOAuthService.unlinkUserByAdmin(kakaoId);
        }

        // 1. 복용 기록/리마인더 → 약물/영양제/처방전 순으로 벌크 삭제 (FK 참조 순서 준수)
        intakeRepository.deleteAllByUserId(userId);
        reminderRepository.deleteAllByUserId(userId);
        userMedicationRepository.deleteAllByUserId(userId);
        userSupplementRepository.deleteAllByUserId(userId);
        prescriptionRepository.deleteAllByUserId(userId);

        // 2. 분석/건강 기록 벌크 삭제
        analysisReportRepository.deleteAllByUserId(userId);
        analysisTemporaryNoteRepository.deleteAllByUserId(userId);
        userAnalysisQuotaRepository.deleteAllByUserId(userId);
        healthNoteRepository.deleteAllByUserId(userId);

        // 3. 문의/가족 연결 벌크 삭제
        qnAReplyRepository.deleteAllByUserId(userId);
        qnARepository.deleteAllByUserId(userId);
        familyLinkRepository.deleteAllByUserId(userId);
        familyLinkRequestRepository.deleteAllByUserId(userId);

        // 4. RefreshToken 및 사용자 삭제
        refreshTokenRepository.deleteByUser(user);
        userRepository.delete(user);

        log.info("회원 탈퇴 완료 - userId: {}", userId);
    }

    @Override
    public UserResponseDTO.NotificationSettings getNotificationSettings(Long userId) {
        User user = findById(userId);
        return UserConverter.toNotificationSettings(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.NotificationSettings updateNotificationSettings(Long userId, UserRequestDTO.UpdateNotificationSettings request) {
        User user = findById(userId);
        user.updateNotificationEnabled(request.getNotificationEnabled());
        log.info("알림 설정 변경 - userId: {}, notificationEnabled: {}", userId, request.getNotificationEnabled());
        return UserConverter.toNotificationSettings(user);
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

    @Override
    @Transactional
    public UserResponseDTO.ProfileSetupResult setupProfile(Long userId, UserRequestDTO.ProfileSetupRequest request) {
        User user = findById(userId);

        // List<SignupPurpose> -> 콤마 구분 문자열로 변환
        String signupPurposesStr = request.getSignupPurposes().stream()
                .map(Enum::name)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        user.updateProfileSetup(request.getGender(), request.getAgeRange(), signupPurposesStr);

        log.info("기본정보 설정 완료 - userId: {}, gender: {}, ageRange: {}, purposes: {}",
                userId, request.getGender(), request.getAgeRange(), signupPurposesStr);

        return UserConverter.toProfileSetupResult(user, request.getSignupPurposes());
    }

    @Override
    public UserResponseDTO.AiConsentStatus getAiConsentStatus(Long userId) {
        User user = findById(userId);
        return UserConverter.toAiConsentStatus(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.AiConsentStatus updateAiConsent(Long userId, UserRequestDTO.UpdateAiConsentRequest request) {
        User user = findById(userId);
        user.updateAiConsent(request.getAiDataAgreed(), request.getConsentVersion());
        log.info("AI 데이터 동의 설정 변경 - userId: {}, aiDataAgreed: {}, consentVersion: {}",
                userId, request.getAiDataAgreed(), request.getConsentVersion());
        return UserConverter.toAiConsentStatus(user);
    }

    @Override
    public UserResponseDTO.ConsentStatus getConsentStatus(Long userId) {
        User user = findById(userId);
        return UserConverter.toConsentStatus(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.ConsentStatus submitConsent(Long userId, UserRequestDTO.ConsentRequest request) {
        User user = findById(userId);
        user.updateConsent(
                request.getTermsAgreed(),
                request.getPrivacyAgreed(),
                user.getAiDataAgreed(),  // AI 동의 상태는 유지
                request.getConsentVersion()
        );
        log.info("서비스 이용 동의 제출 - userId: {}, termsAgreed: {}, privacyAgreed: {}, consentVersion: {}",
                userId, request.getTermsAgreed(), request.getPrivacyAgreed(), request.getConsentVersion());
        return UserConverter.toConsentStatus(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.PhoneUpdateResult updatePhone(Long userId, UserRequestDTO.UpdatePhoneRequest request) {
        User user = findById(userId);

        // 전화번호 해시 생성
        String phoneHash = phoneHashUtil.hash(request.getPhone());

        // 이미 다른 사용자가 사용 중인 전화번호인지 확인 (해시로 검색)
        userRepository.findByPhoneHash(phoneHash).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(userId)) {
                throw new GeneralException(ErrorStatus.FAMILY_PHONE_ALREADY_EXISTS);
            }
        });

        user.updatePhone(request.getPhone(), phoneHash);
        log.info("전화번호 수정 - userId: {}, phone: {}", userId, maskPhone(request.getPhone()));

        return UserConverter.toPhoneUpdateResult(user);
    }

    /**
     * 전화번호 마스킹 (로깅용)
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 10) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
