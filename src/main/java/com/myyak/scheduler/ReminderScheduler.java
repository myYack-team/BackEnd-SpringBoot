package com.myyak.scheduler;

import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.repository.ReminderRepository;
import com.myyak.service.fcmService.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
