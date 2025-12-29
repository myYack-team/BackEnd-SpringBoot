package com.myyak.service.userService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.UserConverter;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.repository.*;
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
    private final RefreshTokenRepository refreshTokenRepository;

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
    public void deleteMe(Long userId) {
        User user = findById(userId);
        log.info("회원 탈퇴 시작 - userId: {}, name: {}", userId, user.getName());

        // 1. 사용자의 약물 목록 조회
        List<UserMedication> medications = userMedicationRepository.findByUser(user);

        // 2. 각 약물의 복용 기록 삭제
        for (UserMedication medication : medications) {
            intakeRepository.deleteAll(intakeRepository.findByUserMedication(medication));
            reminderRepository.deleteByUserMedication(medication);
        }

        // 3. 사용자의 영양제 목록 조회 및 리마인더 삭제
        List<UserSupplement> supplements = userSupplementRepository.findByUser(user);
        for (UserSupplement supplement : supplements) {
            reminderRepository.deleteByUserSupplement(supplement);
        }

        // 4. 약물 삭제
        userMedicationRepository.deleteAll(medications);

        // 5. 영양제 삭제
        userSupplementRepository.deleteAll(supplements);

        // 6. 처방전 삭제
        prescriptionRepository.deleteAll(prescriptionRepository.findByUserOrderByPrescriptionDateDesc(user));

        // 7. RefreshToken 삭제
        refreshTokenRepository.deleteByUser(user);

        // 8. 사용자 삭제
        userRepository.delete(user);

        log.info("회원 탈퇴 완료 - userId: {}", userId);
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
