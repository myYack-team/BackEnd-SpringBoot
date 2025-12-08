package com.myyak.service.fcmService;

import com.myyak.domain.Reminder;

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
     * 복약 알림 발송
     */
    void sendMedicationReminder(Reminder reminder);

    /**
     * 다건 푸시 알림 발송
     */
    void sendNotifications(List<String> fcmTokens, String title, String body);
}
