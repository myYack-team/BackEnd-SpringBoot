package com.myyak.service.familyService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.FamilyConverter;
import com.myyak.domain.*;
import com.myyak.domain.enums.FamilyLinkRequestStatus;
import com.myyak.repository.*;
import com.myyak.service.userService.UserService;
import com.myyak.util.PhoneHashUtil;
import com.myyak.web.dto.FamilyDTO.FamilyRequestDTO;
import com.myyak.web.dto.FamilyDTO.FamilyResponseDTO;
import com.myyak.web.dto.TodayDTO.TodayResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyServiceImpl implements FamilyService {

    private static final int MAX_FAMILY_LINK_COUNT = 1; // MVP: 최대 1명

    private final UserService userService;
    private final UserRepository userRepository;
    private final FamilyLinkRepository familyLinkRepository;
    private final FamilyLinkRequestRepository familyLinkRequestRepository;
    private final ReminderRepository reminderRepository;
    private final IntakeRepository intakeRepository;
    private final PhoneHashUtil phoneHashUtil;

    @Override
    public FamilyResponseDTO.LinkStatus getLinkStatus(Long userId) {
        userService.findById(userId);

        // 내가 보호자로서 연결한 피보호자 목록
        List<FamilyResponseDTO.LinkedFamily> linkedFamilies = familyLinkRepository
                .findByGuardianId(userId)
                .stream()
                .map(FamilyConverter::toLinkedFamily)
                .collect(Collectors.toList());

        // 나를 피보호자로 등록한 보호자 목록
        List<FamilyResponseDTO.Guardian> guardians = familyLinkRepository
                .findByProtectedUserId(userId)
                .stream()
                .map(FamilyConverter::toGuardian)
                .collect(Collectors.toList());

        // 내가 받은 연동 요청
        List<FamilyResponseDTO.PendingRequest> receivedRequests = familyLinkRequestRepository
                .findByTargetIdAndStatus(userId, FamilyLinkRequestStatus.PENDING)
                .stream()
                .map(FamilyConverter::toReceivedPendingRequest)
                .collect(Collectors.toList());

        // 내가 보낸 연동 요청
        List<FamilyResponseDTO.PendingRequest> sentRequests = familyLinkRequestRepository
                .findByRequesterIdAndStatus(userId, FamilyLinkRequestStatus.PENDING)
                .stream()
                .map(FamilyConverter::toSentPendingRequest)
                .collect(Collectors.toList());

        return FamilyResponseDTO.LinkStatus.builder()
                .linkedFamilies(linkedFamilies)
                .guardians(guardians)
                .receivedRequests(receivedRequests)
                .sentRequests(sentRequests)
                .maxLinkCount(MAX_FAMILY_LINK_COUNT)
                .build();
    }

    @Override
    @Transactional
    public FamilyResponseDTO.SendRequestResult sendLinkRequest(Long requesterId, FamilyRequestDTO.SendRequest request) {
        User requester = userService.findById(requesterId);

        // 1. 요청자 전화번호 확인
        if (requester.getPhone() == null || requester.getPhone().isBlank()) {
            throw new GeneralException(ErrorStatus.FAMILY_PHONE_NOT_REGISTERED);
        }

        // 2. 대상자 조회 (전화번호 해시로 검색)
        String phoneHash = phoneHashUtil.hash(request.getPhone());
        User target = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_USER_NOT_FOUND));

        // 3. 자기 자신 체크
        if (target.getId().equals(requesterId)) {
            throw new GeneralException(ErrorStatus.FAMILY_CANNOT_LINK_SELF);
        }

        // 4. 이미 연동된 관계인지 확인
        if (familyLinkRepository.existsByGuardianIdAndProtectedUserId(requesterId, target.getId())) {
            throw new GeneralException(ErrorStatus.FAMILY_ALREADY_LINKED);
        }

        // 5. 최대 연동 수 확인
        long currentLinkCount = familyLinkRepository.countByGuardianId(requesterId);
        if (currentLinkCount >= MAX_FAMILY_LINK_COUNT) {
            throw new GeneralException(ErrorStatus.FAMILY_LINK_LIMIT_EXCEEDED);
        }

        // 6. 이미 보낸 요청인지 확인
        if (familyLinkRequestRepository.existsByRequesterIdAndTargetIdAndStatus(
                requesterId, target.getId(), FamilyLinkRequestStatus.PENDING)) {
            throw new GeneralException(ErrorStatus.FAMILY_REQUEST_ALREADY_SENT);
        }

        // 7. 요청 생성
        FamilyLinkRequest linkRequest = FamilyLinkRequest.builder()
                .requester(requester)
                .target(target)
                .build();

        familyLinkRequestRepository.save(linkRequest);

        log.info("가족 연동 요청 전송 - requesterId: {}, targetId: {}", requesterId, target.getId());

        return FamilyResponseDTO.SendRequestResult.builder()
                .requestId(linkRequest.getId())
                .targetName(target.getName())
                .message(target.getName() + "님에게 가족 연동 요청을 보냈습니다.")
                .build();
    }

    @Override
    @Transactional
    public void cancelLinkRequest(Long requesterId, Long requestId) {
        FamilyLinkRequest request = familyLinkRequestRepository.findByIdAndRequesterId(requestId, requesterId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND));

        if (request.getStatus() != FamilyLinkRequestStatus.PENDING) {
            throw new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND);
        }

        familyLinkRequestRepository.delete(request);
        log.info("가족 연동 요청 취소 - requesterId: {}, requestId: {}", requesterId, requestId);
    }

    @Override
    @Transactional
    public void acceptLinkRequest(Long targetUserId, Long requestId) {
        FamilyLinkRequest request = familyLinkRequestRepository.findByIdAndTargetId(requestId, targetUserId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND));

        if (request.getStatus() != FamilyLinkRequestStatus.PENDING) {
            throw new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND);
        }

        // 요청자의 최대 연동 수 재확인
        long currentLinkCount = familyLinkRepository.countByGuardianId(request.getRequester().getId());
        if (currentLinkCount >= MAX_FAMILY_LINK_COUNT) {
            throw new GeneralException(ErrorStatus.FAMILY_LINK_LIMIT_EXCEEDED);
        }

        // 가족 연동 생성
        FamilyLink link = FamilyLink.builder()
                .guardian(request.getRequester())
                .protectedUser(request.getTarget())
                .build();

        familyLinkRepository.save(link);

        // 요청 상태 업데이트
        request.updateStatus(FamilyLinkRequestStatus.ACCEPTED);

        log.info("가족 연동 요청 수락 - guardianId: {}, protectedId: {}, linkId: {}",
                request.getRequester().getId(), targetUserId, link.getId());
    }

    @Override
    @Transactional
    public void rejectLinkRequest(Long targetUserId, Long requestId) {
        FamilyLinkRequest request = familyLinkRequestRepository.findByIdAndTargetId(requestId, targetUserId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND));

        if (request.getStatus() != FamilyLinkRequestStatus.PENDING) {
            throw new GeneralException(ErrorStatus.FAMILY_REQUEST_NOT_FOUND);
        }

        request.updateStatus(FamilyLinkRequestStatus.REJECTED);
        log.info("가족 연동 요청 거절 - targetUserId: {}, requestId: {}", targetUserId, requestId);
    }

    @Override
    @Transactional
    public void unlinkFamily(Long userId, Long linkId) {
        FamilyLink link = familyLinkRepository.findById(linkId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FAMILY_LINK_NOT_FOUND));

        // 보호자 또는 피보호자만 연동 해제 가능
        if (!link.getGuardian().getId().equals(userId) && !link.getProtectedUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus.FAMILY_NOT_AUTHORIZED);
        }

        familyLinkRepository.delete(link);
        log.info("가족 연동 해제 - userId: {}, linkId: {}", userId, linkId);
    }

    @Override
    public FamilyResponseDTO.FamilyTodaySchedule getFamilyTodaySchedule(Long guardianId, Long protectedUserId) {
        return getFamilyScheduleForDate(guardianId, protectedUserId, LocalDate.now());
    }

    @Override
    public FamilyResponseDTO.FamilyTodaySchedule getFamilyScheduleForDate(Long guardianId, Long protectedUserId, LocalDate date) {
        // 보호자 권한 확인
        validateGuardianAccess(guardianId, protectedUserId);

        User protectedUser = userService.findById(protectedUserId);

        // 오늘의 복약 일정 조회 (TodayService 로직 재사용)
        TodayResponseDTO.TodayResult schedule = getTodayScheduleForUser(protectedUserId, date);

        return FamilyResponseDTO.FamilyTodaySchedule.builder()
                .familyUserId(protectedUserId)
                .familyUserName(protectedUser.getName())
                .familyProfileImage(protectedUser.getProfileImage())
                .todaySchedule(schedule)
                .build();
    }

    @Override
    public FamilyResponseDTO.FamilyMonthlySummary getFamilyMonthlySummary(Long guardianId, Long protectedUserId, Integer year, Integer month) {
        // 보호자 권한 확인
        validateGuardianAccess(guardianId, protectedUserId);

        User protectedUser = userService.findById(protectedUserId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // 리마인더 조회 (비활성 포함 - 과거 캘린더 기록 보존)
        List<Reminder> reminders = reminderRepository.findAllEnabledByUserIdWithDetailsIncludingInactive(protectedUserId);

        // 해당 월의 모든 복약 기록
        LocalDateTime monthStart = startDate.atStartOfDay();
        LocalDateTime monthEnd = endDate.atTime(LocalTime.MAX);
        List<Intake> monthlyIntakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(protectedUserId, monthStart, monthEnd);

        // 날짜별 복약 기록 그룹화
        Map<LocalDate, List<Intake>> intakesByDate = monthlyIntakes.stream()
                .collect(Collectors.groupingBy(i -> i.getTakenAt().toLocalDate()));

        int totalScheduled = 0;
        int totalTaken = 0;
        List<FamilyResponseDTO.DaySummary> days = new java.util.ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int dailyScheduled = calculateTotalScheduledForDate(reminders, date);
            List<Intake> dayIntakes = intakesByDate.getOrDefault(date, List.of());
            int dailyTaken = Math.min(dayIntakes.size(), dailyScheduled);

            // 상태 결정
            String status;
            if (date.isAfter(today)) {
                status = "PENDING";
            } else if (dailyScheduled == 0) {
                status = "NONE";
            } else if (dailyTaken == dailyScheduled) {
                status = "COMPLETE";
            } else if (dailyTaken > 0) {
                status = "PARTIAL";
            } else {
                status = "MISSED";
            }

            days.add(FamilyResponseDTO.DaySummary.builder()
                    .date(date.toString())
                    .totalScheduled(dailyScheduled)
                    .totalTaken(dailyTaken)
                    .status(status)
                    .build());

            // 오늘까지만 통계에 포함
            if (!date.isAfter(today)) {
                totalScheduled += dailyScheduled;
                totalTaken += dailyTaken;
            }
        }

        int totalMissed = totalScheduled - totalTaken;
        double adherenceRate = totalScheduled > 0 ? (double) totalTaken / totalScheduled * 100 : 0;

        return FamilyResponseDTO.FamilyMonthlySummary.builder()
                .familyUserId(protectedUserId)
                .familyUserName(protectedUser.getName())
                .year(year)
                .month(month)
                .totalScheduled(totalScheduled)
                .totalTaken(totalTaken)
                .totalMissed(totalMissed)
                .adherenceRate(Math.round(adherenceRate * 10) / 10.0)
                .days(days)
                .build();
    }

    @Override
    @Transactional
    public FamilyResponseDTO.FamilyNotificationSettings updateFamilyNotificationSettings(Long userId, FamilyRequestDTO.UpdateFamilyNotificationSettings request) {
        User user = userService.findById(userId);
        user.updateFamilyNotificationEnabled(request.getFamilyNotificationEnabled());
        log.info("가족 알림 설정 변경 - userId: {}, familyNotificationEnabled: {}",
                userId, request.getFamilyNotificationEnabled());

        return FamilyResponseDTO.FamilyNotificationSettings.builder()
                .familyNotificationEnabled(user.getFamilyNotificationEnabled())
                .build();
    }

    @Override
    public FamilyResponseDTO.FamilyNotificationSettings getFamilyNotificationSettings(Long userId) {
        User user = userService.findById(userId);
        return FamilyResponseDTO.FamilyNotificationSettings.builder()
                .familyNotificationEnabled(user.getFamilyNotificationEnabled())
                .build();
    }

    /**
     * 보호자 접근 권한 확인
     */
    private void validateGuardianAccess(Long guardianId, Long protectedUserId) {
        boolean hasAccess = familyLinkRepository.existsByGuardianIdAndProtectedUserId(guardianId, protectedUserId);
        if (!hasAccess) {
            throw new GeneralException(ErrorStatus.FAMILY_NOT_AUTHORIZED);
        }
    }

    /**
     * 특정 사용자의 오늘 복약 일정 조회 (TodayService 로직 재사용)
     */
    private TodayResponseDTO.TodayResult getTodayScheduleForUser(Long userId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Reminder> allReminders = reminderRepository.findAllEnabledByUserIdWithDetails(userId);
        List<Reminder> reminders = allReminders.stream()
                .filter(r -> isReminderActiveOnDate(r, date))
                .collect(Collectors.toList());

        List<Intake> intakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(userId, startOfDay, endOfDay);

        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .filter(Intake::isMedicationIntake)
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        Map<Long, List<Intake>> intakesBySupplementId = intakes.stream()
                .filter(Intake::isSupplementIntake)
                .collect(Collectors.groupingBy(i -> i.getUserSupplement().getId()));

        return com.myyak.converter.TodayConverter.toResult(date, reminders, intakesByMedicationId, intakesBySupplementId);
    }

    /**
     * 해당 날짜에 예정된 복약 횟수 계산
     */
    private int calculateTotalScheduledForDate(List<Reminder> reminders, LocalDate date) {
        return (int) reminders.stream()
                .filter(Reminder::getEnabled)
                .filter(r -> isReminderActiveOnDate(r, date))
                .count();
    }

    /**
     * 리마인더가 해당 날짜에 활성화 상태인지 확인
     */
    private boolean isReminderActiveOnDate(Reminder reminder, LocalDate date) {
        if (reminder.isMedicationReminder()) {
            UserMedication um = reminder.getUserMedication();
            // 비활성화되었으나 endDate 미설정된 기존 데이터 방어
            if (!um.getIsActive() && um.getEndDate() == null) return false;
            LocalDate startDate = um.getStartDate();
            LocalDate endDate = um.getEndDate();
            return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
        } else if (reminder.isSupplementReminder()) {
            UserSupplement us = reminder.getUserSupplement();
            // 비활성화되었으나 endDate 미설정된 기존 데이터 방어
            if (!us.getIsActive() && us.getEndDate() == null) return false;
            LocalDate startDate = us.getStartDate();
            LocalDate endDate = us.getEndDate();
            return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
        }
        return false;
    }
}
