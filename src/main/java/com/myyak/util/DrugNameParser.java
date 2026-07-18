package com.myyak.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약물명 파싱 유틸리티
 *
 * 파싱 규칙:
 * 1. 첫 번째 소괄호 안의 내용을 추출
 * 2. "수출명:", "수출용", "수출명"으로 시작하면 제외
 * 3. 쉼표가 있으면 쉼표 앞부분만 추출
 * 4. 추출된 텍스트에 한글이 포함되어야 함
 *
 * 예시:
 * - "에트라빌10밀리그램정(아미트리프틸린염산염)" → "아미트리프틸린염산염"
 * - "아목사펜캡슐(아목시실린수화물)(수출명:...)" → "아목시실린수화물"
 * - "파무정(프랄리독심염화물,수출명:...)" → "프랄리독심염화물"
 * - "신일살부타몰정(수출명:...)" → null
 */
public class DrugNameParser {

    // 첫 번째 소괄호 내용 추출 패턴
    private static final Pattern FIRST_PAREN_PATTERN = Pattern.compile("^(.+?)\\(([^)]+)\\)");

    // 한글 포함 여부 확인 패턴
    private static final Pattern KOREAN_PATTERN = Pattern.compile("[가-힣]");

    // 제외할 키워드 (수출 관련)
    private static final String[] EXCLUDE_PREFIXES = {"수출명:", "수출명 :", "수출용", "수출명"};

    private DrugNameParser() {
        // 유틸리티 클래스
    }

    /**
     * 약물명에서 표시용 이름 추출
     * 첫 번째 괄호 이전까지의 텍스트 반환
     */
    private static String extractDisplayName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        String trimmed = itemName.trim();
        Matcher matcher = FIRST_PAREN_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 대괄호 처리: [수출명:...] 형태
        int bracketIdx = trimmed.indexOf('[');
        if (bracketIdx > 0) {
            return trimmed.substring(0, bracketIdx).trim();
        }

        // 괄호가 없으면 원본 반환
        return trimmed;
    }

    /**
     * 약물명에서 한글 성분명 추출
     *
     * 규칙:
     * 1. 첫 번째 소괄호 안의 내용 추출
     * 2. 수출 관련 키워드로 시작하면 null
     * 3. 쉼표가 있으면 쉼표 앞부분만 사용
     * 4. 한글이 포함되어야 함
     */
    private static String extractIngredientKr(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        Matcher matcher = FIRST_PAREN_PATTERN.matcher(itemName.trim());
        if (!matcher.find()) {
            return null;
        }

        String content = matcher.group(2).trim();

        // 수출 관련 키워드로 시작하면 제외
        for (String prefix : EXCLUDE_PREFIXES) {
            if (content.startsWith(prefix)) {
                return null;
            }
        }

        // 쉼표가 있으면 쉼표 앞부분만 추출
        int commaIdx = content.indexOf(',');
        if (commaIdx > 0) {
            content = content.substring(0, commaIdx).trim();
        }

        // 한글이 포함되어 있어야 함
        if (!KOREAN_PATTERN.matcher(content).find()) {
            return null;
        }

        return content;
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
