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
     * FCM 토큰 해제 (로그아웃 등으로 더 이상 이 사용자에게 알림을 보내지 않아야 할 때)
     */
    void unregisterToken(Long userId);

    /**
     * 단건 푸시 알림 발송
     */
    void sendNotification(User user, String title, String body);

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

    /**
     * 보호자에게 피보호자 미복용 알림 발송
     */
    void sendFamilyMissedMedicationReminder(User guardian, User protectedUser, List<Reminder> reminders, LocalTime originalTime);
}
