package com.myyak.service.oAuthService.kakaoService;

import com.myyak.web.dto.AuthDTO.KakaoTokenResponse;
import com.myyak.web.dto.AuthDTO.KakaoUserInfo;

/**
 * 카카오 OAuth 서비스 인터페이스
 */
public interface KakaoOAuthService {

    /**
     * 카카오 인가 URL 생성 (동적 redirect_uri + state)
     *
     * @param baseUrl 요청의 base URL (예: http://192.168.45.32:8080)
     * @param state OAuth state 파라미터 (CSRF 방지 및 앱 리다이렉트 URI 전달용)
     * @return 카카오 로그인 페이지 URL
     */
    String getAuthorizationUrl(String baseUrl, String state);

    /**
     * 인가 코드로 카카오 액세스 토큰 교환
     *
     * @param code 카카오에서 받은 인가 코드
     * @param baseUrl 요청의 base URL (redirect_uri 생성용)
     * @return 카카오 토큰 응답
     */
    KakaoTokenResponse exchangeCodeForToken(String code, String baseUrl);

    /**
     * 카카오 액세스 토큰으로 사용자 정보 조회
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 카카오 사용자 정보
     */
    KakaoUserInfo getUserInfo(String accessToken);
}
