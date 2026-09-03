package com.myyak.service.fcmService;

import com.google.firebase.messaging.*;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
public class FcmServiceImpl implements FcmService {

    private static final String NOTIFICATION_TITLE = "마이약";
    private static final String ANDROID_CHANNEL_ID = "medication";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final FirebaseMessaging firebaseMessaging;
    private final UserRepository userRepository;

    @Autowired
    public FcmServiceImpl(
            @Autowired(required = false) FirebaseMessaging firebaseMessaging,
            UserRepository userRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.userRepository = userRepository;

        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging이 주입되지 않았습니다. FCM 기능이 비활성화됩니다.");
        }
    }

    @Override
    @Transactional
    public void registerToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        user.updateFcmToken(fcmToken);
        log.info("FCM 토큰 등록 완료 - userId: {}", userId);
    }

    @Override
    @Transactional
    public void unregisterToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        user.clearFcmToken();
        log.info("FCM 토큰 해제 완료 - userId: {}", userId);
    }

    // 무효 토큰 감지 시 토큰 삭제(쓰기)가 발생할 수 있음
    @Override
    @Transactional
    public void sendNotification(User user, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase가 초기화되지 않아 알림을 발송할 수 없습니다.");
            return;
        }

        String fcmToken = user.getFcmToken();
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM 토큰이 없어 알림을 발송할 수 없습니다. - userId: {}", user.getId());
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .setSound("default")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.info("FCM 알림 발송 성공 - response: {}", response);

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                log.warn("무효한 FCM 토큰 감지, 토큰 삭제 - userId: {}", user.getId());
                user.updateFcmToken(null);
                userRepository.save(user);
            } else {
                log.error("FCM 알림 발송 실패 - userId: {}, errorCode: {}, message: {}",
                        user.getId(), errorCode, e.getMessage());
            }
        }
    }

    // 내부에서 sendNotification 호출 → 무효 토큰 삭제(쓰기) 가능
    @Override
    @Transactional
    public void sendMedicationReminder(Reminder reminder) {
        UserMedication medication = reminder.getUserMedication();
        User user = medication.getUser();

        String title = NOTIFICATION_TITLE;
        String body = String.format("%s 복용시간입니다.", medication.getDrugName());

        sendNotification(user, title, body);
    }

    // 내부에서 sendNotification 호출 → 무효 토큰 삭제(쓰기) 가능
    @Override
    @Transactional
    public void sendGroupedMedicationReminder(User user, List<Reminder> reminders) {
        if (reminders == null || reminders.isEmpty()) {
            return;
        }

        // 알림 설정이 꺼져있으면 발송하지 않음
        if (!user.getNotificationEnabled()) {
            log.debug("알림 설정이 꺼져있어 발송하지 않음 - userId: {}", user.getId());
            return;
        }

        String body = buildMedicationReminderBody(reminders);
        sendNotification(user, NOTIFICATION_TITLE, body);
    }

    // 내부에서 sendNotification 호출 → 무효 토큰 삭제(쓰기) 가능
    @Override
    @Transactional
    public void sendMissedMedicationReminder(User user, List<Reminder> reminders, LocalTime originalTime) {
        if (reminders == null || reminders.isEmpty()) {
            return;
        }

        // 알림 설정이 꺼져있으면 발송하지 않음
        if (!user.getNotificationEnabled()) {
            log.debug("알림 설정이 꺼져있어 발송하지 않음 - userId: {}", user.getId());
            return;
        }

        String timeStr = originalTime.format(TIME_FORMATTER);
        String body = String.format("%s의 약 %d개를 아직 먹지 않았습니다.", timeStr, reminders.size());

        sendNotification(user, NOTIFICATION_TITLE, body);
    }

    /**
     * 복약 알림 본문 생성
     * - 1개: "{약 이름} 복용시간입니다."
     * - 2개 이상: "{약 이름} 외 {n}개의 약 복용시간입니다."
     */
    private String buildMedicationReminderBody(List<Reminder> reminders) {
        String firstDrugName = getDrugName(reminders.get(0));
        int count = reminders.size();

        if (count == 1) {
            return String.format("%s 복용시간입니다.", firstDrugName);
        } else {
            return String.format("%s 외 %d개의 약 복용시간입니다.", firstDrugName, count - 1);
        }
    }

    /**
     * Reminder에서 약 이름 추출 (의약품 또는 영양제)
     */
    private String getDrugName(Reminder reminder) {
        if (reminder.getUserMedication() != null) {
            return reminder.getUserMedication().getDrugName();
        } else if (reminder.getUserSupplement() != null) {
            return reminder.getUserSupplement().getSupplementName();
        }
        return "약";
    }

    @Override
    public void sendNotifications(List<String> fcmTokens, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase가 초기화되지 않아 알림을 발송할 수 없습니다.");
            return;
        }

        List<String> validTokens = fcmTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .toList();

        if (validTokens.isEmpty()) {
            log.warn("유효한 FCM 토큰이 없어 알림을 발송할 수 없습니다.");
            return;
        }

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(validTokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .setSound("default")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            log.info("FCM 다건 알림 발송 - 성공: {}, 실패: {}",
                    response.getSuccessCount(), response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            log.error("FCM 다건 알림 발송 실패: {}", e.getMessage());
        }
    }

    // 내부에서 sendNotification 호출 → 무효 토큰 삭제(쓰기) 가능
    @Override
    @Transactional
    public void sendFamilyMissedMedicationReminder(User guardian, User protectedUser, List<Reminder> reminders, LocalTime originalTime) {
        if (reminders == null || reminders.isEmpty()) {
            return;
        }

        // 보호자 알림 설정 확인
        if (!guardian.getNotificationEnabled()) {
            log.debug("보호자 알림 설정이 꺼져있어 발송하지 않음 - guardianId: {}", guardian.getId());
            return;
        }

        // 피보호자 가족 알림 설정 확인
        if (!protectedUser.getFamilyNotificationEnabled()) {
            log.debug("피보호자 가족 알림 설정이 꺼져있어 발송하지 않음 - protectedUserId: {}", protectedUser.getId());
            return;
        }

        String timeStr = originalTime.format(TIME_FORMATTER);
        String firstDrugName = getDrugName(reminders.get(0));
        String body;

        if (reminders.size() == 1) {
            body = String.format("%s님의 %s - %s이(가) 복용처리되지 않았습니다.",
                    protectedUser.getName(), timeStr, firstDrugName);
        } else {
            body = String.format("%s님의 %s - %s 외 %d개가 복용처리되지 않았습니다.",
                    protectedUser.getName(), timeStr, firstDrugName, reminders.size() - 1);
        }

        sendNotification(guardian, NOTIFICATION_TITLE, body);
        log.info("가족 미복용 알림 발송 - guardianId: {}, protectedUserId: {}, reminders: {}",
                guardian.getId(), protectedUser.getId(), reminders.size());
    }
}
