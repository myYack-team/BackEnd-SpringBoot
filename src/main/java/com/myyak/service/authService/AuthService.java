package com.myyak.service.authService;

import com.myyak.web.dto.AuthDTO.AuthRequestDTO;
import com.myyak.web.dto.AuthDTO.AuthResponseDTO;

/**
 * 인증 서비스 인터페이스
 */
public interface AuthService {

    /**
     * 카카오 인가 URL 조회 (동적 redirect_uri)
     *
     * @param baseUrl 요청의 base URL (예: http://192.168.45.32:8080)
     * @return 카카오 로그인 페이지 URL
     */
    String getKakaoAuthorizationUrl(String baseUrl);

    /**
     * 카카오 인가 코드로 로그인 (서버 기반 OAuth 플로우)
     *
     * @param code 카카오에서 받은 인가 코드
     * @param baseUrl 요청의 base URL (redirect_uri 생성용)
     * @return 로그인 응답 (JWT 토큰 + 사용자 정보)
     */
    AuthResponseDTO.LoginResponse loginWithKakaoCode(String code, String baseUrl);

    /**
     * 카카오 액세스 토큰으로 로그인 (하위 호환용)
     *
     * @param request 카카오 액세스 토큰
     * @return 로그인 응답 (JWT 토큰 + 사용자 정보)
     */
    AuthResponseDTO.LoginResponse loginWithKakao(AuthRequestDTO.KakaoLoginRequest request);

    /**
     * 토큰 갱신
     *
     * @param request 리프레시 토큰
     * @return 새로운 토큰
     */
    AuthResponseDTO.TokenResponse refreshToken(AuthRequestDTO.RefreshRequest request);

    /**
     * 로그아웃 (리프레시 토큰 삭제)
     *
     * @param userId 사용자 ID
     */
    void logout(Long userId);
}
