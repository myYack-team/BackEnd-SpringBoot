package com.myyak.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrugNameParserTest {

    @Test
    void testBasicParsing() {
        // 기본 케이스: 순수 한글 성분명
        var result = DrugNameParser.parse("에트라빌10밀리그램정(아미트리프틸린염산염)");
        assertEquals("에트라빌10밀리그램정", result.displayName());
        assertEquals("아미트리프틸린염산염", result.ingredientKr());
    }

    @Test
    void testMultipleParentheses() {
        // 첫 번째 괄호가 성분명
        var result = DrugNameParser.parse("아목사펜캡슐(아목시실린수화물)(수출명:아드목스캡슐)");
        assertEquals("아목사펜캡슐", result.displayName());
        assertEquals("아목시실린수화물", result.ingredientKr());
    }

    @Test
    void testCommaIngredient() {
        // 쉼표 앞부분만 추출
        var result = DrugNameParser.parse("파무정(프랄리독심염화물,수출명:PralidoximChlorideTab.)");
        assertEquals("파무정", result.displayName());
        assertEquals("프랄리독심염화물", result.ingredientKr());
    }

    @Test
    void testExportNameOnly() {
        // 수출명으로 시작하면 null
        var result = DrugNameParser.parse("신일살부타몰정(수출명:BUTAMOLTab.)(SalbutamolSulfate)");
        assertEquals("신일살부타몰정", result.displayName());
        assertNull(result.ingredientKr());
    }

    @Test
    void testSquareBrackets() {
        // 대괄호는 무시
        var result = DrugNameParser.parse("대원피토나디온주사액[수출명:피톤주사액]");
        assertEquals("대원피토나디온주사액", result.displayName());
        assertNull(result.ingredientKr());
    }

    @Test
    void testExportSuffix() {
        // (수출용) 형태
        var result = DrugNameParser.parse("탐부톨정400밀리그램(에탐부톨염산염)(수출용)");
        assertEquals("탐부톨정400밀리그램", result.displayName());
        assertEquals("에탐부톨염산염", result.ingredientKr());
    }

    @Test
    void testEnglishOnly() {
        // 영문만 있으면 null
        var result = DrugNameParser.parse("타이레놀정(Acetaminophen)");
        assertEquals("타이레놀정", result.displayName());
        assertNull(result.ingredientKr());
    }

    @Test
    void testKoreanIngredient() {
        // 한글 성분명
        var result = DrugNameParser.parse("타이레놀정500밀리그람(아세트아미노펜)");
        assertEquals("타이레놀정500밀리그람", result.displayName());
        assertEquals("아세트아미노펜", result.ingredientKr());
    }
}
