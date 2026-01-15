package com.myyak.service.authService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.RefreshToken;
import com.myyak.domain.User;
import com.myyak.repository.RefreshTokenRepository;
import com.myyak.repository.UserRepository;
import com.myyak.service.oAuthService.kakaoService.KakaoOAuthService;
import com.myyak.util.JwtProvider;
import com.myyak.web.dto.AuthDTO.AuthRequestDTO;
import com.myyak.web.dto.AuthDTO.AuthResponseDTO;
import com.myyak.web.dto.AuthDTO.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 인증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final KakaoOAuthService kakaoOAuthService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    @Override
    public String getKakaoAuthorizationUrl(String baseUrl, String state) {
        return kakaoOAuthService.getAuthorizationUrl(baseUrl, state);
    }

    @Override
    public AuthResponseDTO.LoginResponse loginWithKakaoCode(String code, String baseUrl) {
        // 1. 인가 코드로 카카오 액세스 토큰 교환 (동적 redirect_uri 사용)
        var kakaoToken = kakaoOAuthService.exchangeCodeForToken(code, baseUrl);

        // 2. 카카오 액세스 토큰으로 사용자 정보 조회
        KakaoUserInfo kakaoUserInfo = kakaoOAuthService.getUserInfo(kakaoToken.getAccessToken());

        // 3. 로그인/회원가입 처리
        return processKakaoLogin(kakaoUserInfo);
    }

    @Override
    public AuthResponseDTO.LoginResponse loginWithKakao(AuthRequestDTO.KakaoLoginRequest request) {
        // 카카오에서 사용자 정보 조회 (하위 호환용)
        KakaoUserInfo kakaoUserInfo = kakaoOAuthService.getUserInfo(request.getAccessToken());
        return processKakaoLogin(kakaoUserInfo);
    }

    /**
     * 카카오 사용자 정보로 로그인/회원가입 처리
     */
    private AuthResponseDTO.LoginResponse processKakaoLogin(KakaoUserInfo kakaoUserInfo) {
        if (kakaoUserInfo == null || kakaoUserInfo.getId() == null) {
            throw new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED);
        }

        String kakaoId = String.valueOf(kakaoUserInfo.getId());

        // 기존 사용자 조회 또는 신규 생성
        Optional<User> existingUser = userRepository.findByKakaoId(kakaoId);
        boolean isNewUser = existingUser.isEmpty();

        User user;
        if (isNewUser) {
            // 신규 사용자 생성
            user = createNewUser(kakaoId, kakaoUserInfo);
            log.info("신규 사용자 생성: userId={}, kakaoId={}", user.getId(), kakaoId);
        } else {
            // 기존 사용자 정보 업데이트
            user = existingUser.get();
            updateUserFromKakao(user, kakaoUserInfo);
            log.info("기존 사용자 로그인: userId={}, kakaoId={}", user.getId(), kakaoId);
        }

        // JWT 토큰 생성
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // Refresh Token 저장
        saveRefreshToken(user, refreshToken);

        // 응답 생성
        return AuthResponseDTO.LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtProvider.getAccessTokenExpiry())
                .user(toUserInfo(user))
                .isNewUser(isNewUser)
                .build();
    }

    @Override
    public AuthResponseDTO.TokenResponse refreshToken(AuthRequestDTO.RefreshRequest request) {
        String refreshTokenValue = request.getRefreshToken();

        // 1. Refresh Token 유효성 검증
        if (!jwtProvider.validateToken(refreshTokenValue)) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }

        // 2. Refresh Token이 access 타입이 아닌지 확인
        if (!"refresh".equals(jwtProvider.getTokenType(refreshTokenValue))) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }

        // 3. DB에서 Refresh Token 조회
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN));

        // 4. 토큰 만료 확인
        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new GeneralException(ErrorStatus.AUTH_EXPIRED_TOKEN);
        }

        // 5. 새 토큰 생성
        User user = storedToken.getUser();
        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId());

        // 6. Refresh Token 갱신
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpiry() / 1000);
        storedToken.updateToken(newRefreshToken, expiresAt);

        log.info("토큰 갱신 완료: userId={}", user.getId());

        return AuthResponseDTO.TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(jwtProvider.getAccessTokenExpiry())
                .build();
    }

    @Override
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        refreshTokenRepository.deleteByUser(user);
        log.info("로그아웃 완료: userId={}", userId);
    }

    private User createNewUser(String kakaoId, KakaoUserInfo kakaoUserInfo) {
        KakaoUserInfo.KakaoAccount account = kakaoUserInfo.getKakaoAccount();
        KakaoUserInfo.Profile profile = account != null ? account.getProfile() : null;

        String name = profile != null ? profile.getNickname() : "사용자";
        String email = account != null ? account.getEmail() : null;
        String profileImage = profile != null ? toHttpsUrl(profile.getProfileImageUrl()) : null;

        User user = User.builder()
                .kakaoId(kakaoId)
                .name(name)
                .email(email)
                .profileImage(profileImage)
                .build();

        return userRepository.save(user);
    }

    private void updateUserFromKakao(User user, KakaoUserInfo kakaoUserInfo) {
        KakaoUserInfo.KakaoAccount account = kakaoUserInfo.getKakaoAccount();
        KakaoUserInfo.Profile profile = account != null ? account.getProfile() : null;

        String name = profile != null ? profile.getNickname() : user.getName();
        String email = account != null ? account.getEmail() : null;
        String profileImage = profile != null ? toHttpsUrl(profile.getProfileImageUrl()) : null;

        user.updateKakaoInfo(name, email, profileImage);
    }

    private void saveRefreshToken(User user, String refreshToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpiry() / 1000);

        // 기존 토큰이 있으면 업데이트, 없으면 새로 생성
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUser(user);

        if (existingToken.isPresent()) {
            existingToken.get().updateToken(refreshToken, expiresAt);
        } else {
            RefreshToken newToken = RefreshToken.builder()
                    .user(user)
                    .token(refreshToken)
                    .expiresAt(expiresAt)
                    .build();
            refreshTokenRepository.save(newToken);
        }
    }

    private AuthResponseDTO.UserInfo toUserInfo(User user) {
        return AuthResponseDTO.UserInfo.builder()
                .id(user.getId())
                .kakaoId(user.getKakaoId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .gender(user.getGender())
                .ageRange(user.getAgeRange())
                .signupPurposes(user.getSignupPurposes())
                .fontSize(user.getFontSize())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * HTTP URL을 HTTPS로 변환 (카카오 CDN은 HTTPS 지원)
     */
    private String toHttpsUrl(String url) {
        if (url != null && url.startsWith("http://")) {
            return url.replace("http://", "https://");
        }
        return url;
    }
}
