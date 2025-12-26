package com.myyak.util;

/**
 * 복약 관련 계산 유틸리티 클래스
 */
public final class MedicationCalculator {

    private MedicationCalculator() {
        // 인스턴스화 방지
    }

    /**
     * 남은 복용 일수 계산
     *
     * @param remainingCount 남은 약 개수
     * @param frequency      하루 복용 횟수
     * @param dosage         1회 복용량 (예: "1정", "2정")
     * @return 남은 복용 일수
     */
    public static int calculateDaysLeft(Integer remainingCount, Integer frequency, String dosage) {
        if (remainingCount == null || remainingCount <= 0) {
            return 0;
        }
        if (frequency == null || frequency <= 0) {
            return remainingCount;
        }

        int dosagePerTime = parseDosage(dosage);
        int dailyUsage = frequency * dosagePerTime;

        return (int) Math.ceil((double) remainingCount / dailyUsage);
    }

    /**
     * 복용량 문자열에서 숫자 추출
     *
     * @param dosage 복용량 문자열 (예: "1정", "2정")
     * @return 복용량 숫자 (파싱 실패 시 1 반환)
     */
    public static int parseDosage(String dosage) {
        if (dosage == null || dosage.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(dosage.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
