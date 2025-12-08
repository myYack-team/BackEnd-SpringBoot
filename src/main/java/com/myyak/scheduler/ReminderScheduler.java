package com.myyak.scheduler;

import com.myyak.domain.Reminder;
import com.myyak.repository.ReminderRepository;
import com.myyak.service.fcmService.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final FcmService fcmService;

    /**
     * 매 분마다 알림 체크 및 발송
     * 현재 시간(분 단위)에 해당하는 활성화된 알림을 찾아 FCM 발송
     */
    @Scheduled(cron = "0 * * * * *")  // 매 분 0초에 실행
    public void sendScheduledReminders() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<Reminder> reminders = reminderRepository.findByTimeAndEnabledTrue(now);

        if (reminders.isEmpty()) {
            return;
        }

        log.info("복약 알림 발송 시작 - 시간: {}, 건수: {}", now, reminders.size());

        for (Reminder reminder : reminders) {
            try {
                fcmService.sendMedicationReminder(reminder);
            } catch (Exception e) {
                log.error("알림 발송 실패 - reminderId: {}, error: {}",
                        reminder.getId(), e.getMessage());
            }
        }

        log.info("복약 알림 발송 완료 - 시간: {}", now);
    }
}
