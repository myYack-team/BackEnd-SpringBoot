package com.myyak.service.todayService;

import com.myyak.converter.TodayConverter;
import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.repository.IntakeRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.TodayDTO.TodayResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayServiceImpl implements TodayService {

    private final ReminderRepository reminderRepository;
    private final IntakeRepository intakeRepository;
    private final UserService userService;

    @Override
    public TodayResponseDTO.TodayResult getTodaySchedule(Long userId) {
        userService.findById(userId);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 약물 + 영양제 리마인더 통합 조회 (날짜 기준 필터링)
        List<Reminder> allReminders = reminderRepository.findAllEnabledByUserIdWithDetails(userId);
        List<Reminder> reminders = allReminders.stream()
                .filter(r -> isReminderActiveOnDate(r, today))
                .collect(Collectors.toList());

        // 약물 + 영양제 복용 기록 통합 조회
        List<Intake> intakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(userId, startOfDay, endOfDay);

        // 약물 복용 기록: medicationId -> List<Intake>
        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .filter(Intake::isMedicationIntake)
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        // 영양제 복용 기록: supplementId -> List<Intake>
        Map<Long, List<Intake>> intakesBySupplementId = intakes.stream()
                .filter(Intake::isSupplementIntake)
                .collect(Collectors.groupingBy(i -> i.getUserSupplement().getId()));

        return TodayConverter.toResult(today, reminders, intakesByMedicationId, intakesBySupplementId);
    }

    /**
     * 리마인더가 해당 날짜에 활성화 상태인지 확인 (약물/영양제 모두 지원)
     */
    private boolean isReminderActiveOnDate(Reminder reminder, LocalDate date) {
        if (reminder.isMedicationReminder()) {
            UserMedication um = reminder.getUserMedication();
            if (!um.getIsActive()) return false;
            LocalDate startDate = um.getStartDate();
            LocalDate endDate = um.getEndDate();
            return !date.isBefore(startDate) && (endDate == null || date.isBefore(endDate));
        } else if (reminder.isSupplementReminder()) {
            UserSupplement us = reminder.getUserSupplement();
            if (!us.getIsActive()) return false;
            LocalDate startDate = us.getStartDate();
            LocalDate endDate = us.getEndDate();
            return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
        }
        return false;
    }
}
