package com.myyak.scheduler;

import com.myyak.config.NotificationConstants;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.repository.IntakeRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.service.fcmService.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final IntakeRepository intakeRepository;
    private final FcmService fcmService;

    /**
     * 매 분마다 알림 체크 및 발송
     * 현재 시간(분 단위)에 해당하는 활성화된 알림을 찾아 FCM 발송
     * - 의약품 + 영양제 통합 조회
     * - 사용자별로 그룹핑하여 하나의 알림으로 발송
     */
    @Scheduled(cron = "0 * * * * *")  // 매 분 0초에 실행
    public void sendScheduledReminders() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        // 의약품 + 영양제 통합 쿼리
        List<Reminder> reminders = reminderRepository.findAllActiveByTimeAndEnabledTrue(now);

        if (reminders.isEmpty()) {
            return;
        }

        log.info("복약 알림 발송 시작 - 시간: {}, 건수: {}", now, reminders.size());

        // 사용자별로 그룹핑
        Map<User, List<Reminder>> remindersByUser = reminders.stream()
                .collect(Collectors.groupingBy(this::getUserFromReminder));

        // 사용자별로 알림 발송
        for (Map.Entry<User, List<Reminder>> entry : remindersByUser.entrySet()) {
            User user = entry.getKey();
            List<Reminder> userReminders = entry.getValue();

            try {
                fcmService.sendGroupedMedicationReminder(user, userReminders);
                log.debug("알림 발송 - userId: {}, 약 개수: {}", user.getId(), userReminders.size());
            } catch (Exception e) {
                log.error("알림 발송 실패 - userId: {}, error: {}", user.getId(), e.getMessage());
            }
        }

        log.info("복약 알림 발송 완료 - 시간: {}, 사용자 수: {}", now, remindersByUser.size());
    }

    /**
     * 미복용 리마인더 발송
     * 설정된 지연 시간(기본 30분) 전에 알림이 있었지만 아직 복용하지 않은 경우 리마인더 발송
     */
    @Scheduled(cron = "0 * * * * *")  // 매 분 0초에 실행
    public void sendMissedReminders() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalTime originalTime = now.minusMinutes(NotificationConstants.MISSED_REMINDER_DELAY_MINUTES);
        LocalDate today = LocalDate.now();

        // 30분 전 시간에 알림이 있었던 Reminder 조회
        List<Reminder> reminders = reminderRepository.findAllActiveByTimeAndEnabledTrue(originalTime);

        if (reminders.isEmpty()) {
            return;
        }

        // 복용하지 않은 Reminder만 필터링
        List<Reminder> missedReminders = reminders.stream()
                .filter(reminder -> !hasIntakeRecord(reminder, today))
                .toList();

        if (missedReminders.isEmpty()) {
            return;
        }

        log.info("미복용 리마인더 발송 시작 - 원래 시간: {}, 건수: {}", originalTime, missedReminders.size());

        // 사용자별로 그룹핑
        Map<User, List<Reminder>> remindersByUser = missedReminders.stream()
                .collect(Collectors.groupingBy(this::getUserFromReminder));

        // 사용자별로 미복용 리마인더 발송
        for (Map.Entry<User, List<Reminder>> entry : remindersByUser.entrySet()) {
            User user = entry.getKey();
            List<Reminder> userReminders = entry.getValue();

            try {
                fcmService.sendMissedMedicationReminder(user, userReminders, originalTime);
                log.debug("미복용 리마인더 발송 - userId: {}, 약 개수: {}", user.getId(), userReminders.size());
            } catch (Exception e) {
                log.error("미복용 리마인더 발송 실패 - userId: {}, error: {}", user.getId(), e.getMessage());
            }
        }

        log.info("미복용 리마인더 발송 완료 - 원래 시간: {}, 사용자 수: {}", originalTime, remindersByUser.size());
    }

    /**
     * 해당 Reminder에 대한 오늘의 복용 기록이 있는지 확인
     */
    private boolean hasIntakeRecord(Reminder reminder, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        if (reminder.getUserMedication() != null) {
            return intakeRepository.existsByUserMedicationIdAndTimingAndDateRange(
                    reminder.getUserMedication().getId(),
                    reminder.getTiming(),
                    startOfDay,
                    endOfDay
            );
        } else if (reminder.getUserSupplement() != null) {
            return intakeRepository.existsByUserSupplementIdAndTimingAndDateRange(
                    reminder.getUserSupplement().getId(),
                    reminder.getTiming(),
                    startOfDay,
                    endOfDay
            );
        }
        return false;
    }

    /**
     * Reminder에서 User 추출
     */
    private User getUserFromReminder(Reminder reminder) {
        if (reminder.getUserMedication() != null) {
            return reminder.getUserMedication().getUser();
        } else if (reminder.getUserSupplement() != null) {
            return reminder.getUserSupplement().getUser();
        }
        throw new IllegalStateException("Reminder에 연결된 약물 또는 영양제가 없습니다.");
    }
}
