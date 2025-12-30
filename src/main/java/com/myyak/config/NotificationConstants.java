package com.myyak.config;

/**
 * 알림 관련 상수
 * 미복용 리마인더 시간 등 알림 정책을 여기서 관리합니다.
 */
public final class NotificationConstants {

    private NotificationConstants() {
        // 인스턴스화 방지
    }

    /**
     * 미복용 리마인더 지연 시간 (분)
     * 원래 알림 시간 이후 이 시간이 지나도 복용하지 않으면 리마인더 발송
     */
    public static final int MISSED_REMINDER_DELAY_MINUTES = 30;
}
