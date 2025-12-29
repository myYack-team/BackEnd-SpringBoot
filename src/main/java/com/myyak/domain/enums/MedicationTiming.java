package com.myyak.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor
public enum MedicationTiming {

    MORNING("아침", LocalTime.of(8, 0)),
    AFTERNOON("점심", LocalTime.of(12, 30)),
    EVENING("저녁", LocalTime.of(18, 30)),
    AS_NEEDED("필요시", null);

    private final String description;
    private final LocalTime defaultTime;

    /**
     * 시간을 기반으로 Timing을 자동 계산합니다.
     * - 03:00 ~ 10:59 → MORNING (아침)
     * - 11:00 ~ 16:59 → AFTERNOON (점심)
     * - 17:00 ~ 02:59 → EVENING (저녁)
     */
    public static MedicationTiming fromTime(LocalTime time) {
        if (time == null) {
            return AS_NEEDED;
        }

        int hour = time.getHour();

        // 03:00 ~ 10:59 → MORNING
        if (hour >= 3 && hour < 11) {
            return MORNING;
        }
        // 11:00 ~ 16:59 → AFTERNOON
        if (hour >= 11 && hour < 17) {
            return AFTERNOON;
        }
        // 17:00 ~ 02:59 → EVENING (17~23시 또는 0~2시)
        return EVENING;
    }
}
