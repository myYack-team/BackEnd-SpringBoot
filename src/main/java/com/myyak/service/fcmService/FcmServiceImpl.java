package com.myyak.service.fcmService;

import com.google.firebase.messaging.*;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FcmServiceImpl implements FcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final UserRepository userRepository;

    @Override
    public void registerToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        user.updateFcmToken(fcmToken);
        log.info("FCM 토큰 등록 완료 - userId: {}", userId);
    }

    @Override
    public void sendNotification(String fcmToken, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase가 초기화되지 않아 알림을 발송할 수 없습니다.");
            return;
        }

        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM 토큰이 없어 알림을 발송할 수 없습니다.");
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
                                    .setSound("default")
                                    .setClickAction("OPEN_MEDICATION")
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
            log.error("FCM 알림 발송 실패: {}", e.getMessage());
        }
    }

    @Override
    public void sendMedicationReminder(Reminder reminder) {
        UserMedication medication = reminder.getUserMedication();
        User user = medication.getUser();

        String title = "복약 알림";
        String body = String.format("%s 복용 시간입니다.", medication.getDrugName());

        sendNotification(user.getFcmToken(), title, body);
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
                    .build();

            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            log.info("FCM 다건 알림 발송 - 성공: {}, 실패: {}",
                    response.getSuccessCount(), response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            log.error("FCM 다건 알림 발송 실패: {}", e.getMessage());
        }
    }
}
