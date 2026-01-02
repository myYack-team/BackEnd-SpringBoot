package com.myyak.service.llm;

/**
 * LLM 클라이언트 인터페이스 (Port)
 * 모든 LLM 구현체는 이 인터페이스를 구현해야 합니다.
 */
public interface LlmClient {

    /**
     * 텍스트 생성
     * @param systemPrompt 시스템 프롬프트 (역할, 규칙 정의)
     * @param userPrompt 사용자 프롬프트 (입력 데이터)
     * @return 생성된 텍스트 (JSON 등)
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * 현재 사용 중인 LLM 제공자 이름
     */
    String getProviderName();

    /**
     * 현재 사용 중인 모델 이름
     */
    String getModelName();
}
