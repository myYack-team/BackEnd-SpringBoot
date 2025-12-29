package com.myyak.service.fcmService;

import com.myyak.domain.Reminder;
import com.myyak.domain.User;

import java.time.LocalTime;
import java.util.List;

public interface FcmService {

    /**
     * FCM 토큰 등록/갱신
     */
    void registerToken(Long userId, String fcmToken);

    /**
     * 단건 푸시 알림 발송
     */
    void sendNotification(String fcmToken, String title, String body);

    /**
     * 복약 알림 발송 (단건) - deprecated, sendGroupedMedicationReminder 사용 권장
     */
    void sendMedicationReminder(Reminder reminder);

    /**
     * 복약 알림 발송 (그룹핑)
     * 같은 시간에 여러 약이 있으면 하나의 알림으로 묶어서 발송
     */
    void sendGroupedMedicationReminder(User user, List<Reminder> reminders);

    /**
     * 미복용 리마인더 발송
     */
    void sendMissedMedicationReminder(User user, List<Reminder> reminders, LocalTime originalTime);

    /**
     * 다건 푸시 알림 발송
     */
    void sendNotifications(List<String> fcmTokens, String title, String body);
}
