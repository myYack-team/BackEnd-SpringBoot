package com.myyak.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약물명 파싱 유틸리티
 * "메드론정4밀리그람(메틸프레드니솔론)" → displayName: "메드론정4밀리그람", ingredientKr: "메틸프레드니솔론"
 */
public class DrugNameParser {

    // 괄호 안의 한글 성분명 추출 패턴
    // 예: "메드론정4밀리그람(메틸프레드니솔론)" → 그룹1: "메드론정4밀리그람", 그룹2: "메틸프레드니솔론"
    private static final Pattern KOREAN_INGREDIENT_PATTERN =
            Pattern.compile("^(.+?)\\(([가-힣]+)\\)$");

    // 영문 성분명이 있는 경우도 처리
    // 예: "타이레놀정500밀리그람(Acetaminophen)" → 영문은 무시
    private static final Pattern ANY_INGREDIENT_PATTERN =
            Pattern.compile("^(.+?)\\(([^)]+)\\)$");

    private DrugNameParser() {
        // 유틸리티 클래스
    }

    /**
     * 약물명에서 표시용 이름 추출
     * "메드론정4밀리그람(메틸프레드니솔론)" → "메드론정4밀리그람"
     */
    public static String extractDisplayName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        Matcher matcher = ANY_INGREDIENT_PATTERN.matcher(itemName.trim());
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }

        // 괄호가 없으면 원본 반환
        return itemName.trim();
    }

    /**
     * 약물명에서 한글 성분명 추출
     * "메드론정4밀리그람(메틸프레드니솔론)" → "메틸프레드니솔론"
     * 영문 성분명만 있는 경우 null 반환
     */
    public static String extractIngredientKr(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        Matcher matcher = KOREAN_INGREDIENT_PATTERN.matcher(itemName.trim());
        if (matcher.matches()) {
            return matcher.group(2).trim();
        }

        // 한글 성분명이 없으면 null
        return null;
    }

    /**
     * 약물명 파싱 결과를 담는 레코드
     */
    public record ParsedDrugName(String displayName, String ingredientKr) {}

    /**
     * 약물명을 파싱하여 displayName과 ingredientKr 반환
     */
    public static ParsedDrugName parse(String itemName) {
        return new ParsedDrugName(
                extractDisplayName(itemName),
                extractIngredientKr(itemName)
        );
    }
}
