package com.myyak.util;

import com.myyak.web.dto.AnalysisDTO.AnalysisResponseDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 테스트 분석용 시나리오 데이터 클래스
 * Mock 데이터를 통해 약물 데이터가 부족한 사용자도 AI 분석 리포트를 체험할 수 있게 합니다.
 */
@Getter
@Builder
public class TestAnalysisScenario {
    private final String categoryName;
    private final List<MockMedication> medications;
    private final String userPromptJson;

    /**
     * 패턴 분석 프롬프트 JSON을 동적으로 생성합니다.
     * 날짜는 호출 시점 기준으로 최근 30일을 계산하여 사용합니다.
     *
     * @param temporaryNotesJson 사용자의 실제 임시 건강 메모 JSON
     * @return 패턴 분석용 프롬프트 JSON 문자열
     */
    public String getPatternPromptJson(String temporaryNotesJson) {
        return patternPromptJsonSupplier.apply(temporaryNotesJson);
    }

    /**
     * Mock dailyConditions 데이터를 생성합니다.
     * LLM에 의존하지 않고 시나리오 데이터에서 직접 생성합니다.
     */
    public List<AnalysisResponseDTO.DailyCondition> buildDailyConditions() {
        return dailyConditionsSupplier.get();
    }

    /**
     * 패턴 분석 프롬프트 생성 함수
     * 날짜를 동적으로 생성하기 위해 함수형 인터페이스를 사용합니다.
     */
    @Builder.Default
    private final java.util.function.Function<String, String> patternPromptJsonSupplier = (notes) -> "{}";

    /**
     * Mock dailyConditions 생성 함수
     */
    @Builder.Default
    private final java.util.function.Supplier<List<AnalysisResponseDTO.DailyCondition>> dailyConditionsSupplier = List::of;

    @Getter
    @Builder
    public static class MockMedication {
        private final Long id;
        private final String name;
        private final String ingredient;
        private final List<String> timings;
    }
}
