package com.myyak.service.todayService;

import com.myyak.converter.TodayConverter;
import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
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

        // N+1 방지: Fetch Join으로 UserMedication 함께 조회
        List<Reminder> reminders = reminderRepository.findEnabledByUserIdWithMedication(userId);
        List<Intake> intakes = intakeRepository.findByUserIdAndDateRangeWithMedication(userId, startOfDay, endOfDay);

        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        return TodayConverter.toResult(today, reminders, intakesByMedicationId);
    }
}
