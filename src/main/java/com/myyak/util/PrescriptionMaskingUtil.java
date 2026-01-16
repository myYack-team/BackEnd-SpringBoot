package com.myyak.util;

import java.util.regex.Pattern;

/**
 * 처방전 OCR 텍스트에서 민감 정보(PII)를 마스킹하는 유틸리티
 * - 주민등록번호
 * - 환자 이름
 * - 나이 정보
 */
public class PrescriptionMaskingUtil {

    // 1. 주민등록번호: 990223-1032915 -> ******-*******
    private static final Pattern RRN_PATTERN =
            Pattern.compile("(\\d{6})[\\s-]?(\\d{7})");

    // 2. 이름 패턴 (다양한 처방전/약봉투 형식)
    private static final Pattern NAME_PATTERN =
            Pattern.compile(
                    "(성\\s*명|환자명|환자번호/이름|이름|환자|성명)[:\\s]+([가-힣]{2,4})"
            );

    // 3. 나이 정보: (남/만 26세)
    private static final Pattern AGE_PATTERN =
            Pattern.compile("\\([남여]/만\\s*\\d{1,3}세\\)");

    private PrescriptionMaskingUtil() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * 민감 정보를 마스킹 처리
     *
     * @param text OCR로 추출된 원본 텍스트
     * @return 민감 정보가 마스킹된 텍스트
     */
    public static String maskSensitiveInfo(String text) {
        if (text == null) {
            return null;
        }

        String result = text;
        result = RRN_PATTERN.matcher(result).replaceAll("******-*******");
        result = NAME_PATTERN.matcher(result).replaceAll("$1: ***");
        result = AGE_PATTERN.matcher(result).replaceAll("(***)");

        return result;
    }
}
