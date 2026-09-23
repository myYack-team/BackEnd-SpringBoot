package com.myyak.service.authService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.AuthConverter;
import com.myyak.domain.AppSetting;
import com.myyak.domain.User;
import com.myyak.repository.AppSettingRepository;
import com.myyak.repository.UserRepository;
import com.myyak.service.authService.store.RefreshTokenSessionStore;
import com.myyak.service.oAuthService.kakaoService.KakaoOAuthService;
import com.myyak.util.JwtProvider;
import com.myyak.web.dto.AuthDTO.AuthRequestDTO;
import com.myyak.web.dto.AuthDTO.AuthResponseDTO;
import com.myyak.web.dto.AuthDTO.KakaoUserInfo;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 인증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    public static final String TEST_KAKAO_ID = "TEST_REVIEW_ACCOUNT";

    private final KakaoOAuthService kakaoOAuthService;
    private final UserRepository userRepository;
    private final RefreshTokenSessionStore refreshTokenSessionStore;
    private final JwtProvider jwtProvider;
    private final AppSettingRepository appSettingRepository;

    @Override
    public String getKakaoAuthorizationUrl(String baseUrl, String state) {
        return kakaoOAuthService.getAuthorizationUrl(baseUrl, state);
    }

    @Override
    @Transactional
    public AuthResponseDTO.LoginResponse loginWithKakaoCode(String code, String baseUrl) {
        // 1. 인가 코드로 카카오 액세스 토큰 교환 (동적 redirect_uri 사용)
        var kakaoToken = kakaoOAuthService.exchangeCodeForToken(code, baseUrl);

        // 2. 카카오 액세스 토큰으로 사용자 정보 조회
        KakaoUserInfo kakaoUserInfo = kakaoOAuthService.getUserInfo(kakaoToken.getAccessToken());

        // 3. 로그인/회원가입 처리
        return processKakaoLogin(kakaoUserInfo);
    }

    @Override
    @Transactional
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
        String familyId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), familyId);

        // Refresh Token 저장
        refreshTokenSessionStore.issue(user.getId(), refreshToken, familyId,
                jwtProvider.getExpiration(refreshToken).toInstant());

        // 응답 생성
        return AuthConverter.toLoginResponse(
                accessToken, refreshToken, jwtProvider.getAccessTokenExpiry(), user, isNewUser);
    }

    @Override
    @Transactional
    public AuthResponseDTO.TokenResponse refreshToken(AuthRequestDTO.RefreshRequest request) {
        String refreshTokenValue = request.getRefreshToken();

        // 1. Refresh Token 서명 및 만료 검증
        Claims claims = jwtProvider.validateAndParseRefreshToken(refreshTokenValue);

        // 2. Refresh Token이 access 타입이 아닌지 확인
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }

        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN));

        // Redis 세션은 현재 family를 보존한다. 기존 RDS 토큰은 다음 회전 때 family를 부여한다.
        String familyId = refreshTokenSessionStore.currentFamily(userId);
        if (familyId == null) {
            familyId = claims.get("family", String.class);
        }
        if (familyId == null) {
            familyId = UUID.randomUUID().toString();
        }

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), familyId);

        // 저장소에서 이전 토큰 폐기와 새 토큰 저장을 원자적으로 처리한다.
        refreshTokenSessionStore.rotate(userId, refreshTokenValue, newRefreshToken, familyId,
                claims.getExpiration().toInstant(), jwtProvider.getExpiration(newRefreshToken).toInstant());

        log.info("토큰 갱신 완료: userId={}", user.getId());

        return AuthConverter.toTokenResponse(
                newAccessToken, newRefreshToken, jwtProvider.getAccessTokenExpiry());
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        // 공용 기기에서 다음 사용자에게 알림이 전달되지 않도록 토큰 해제
        user.clearFcmToken();
        refreshTokenSessionStore.revoke(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }

    @Override
    @Transactional
    public AuthResponseDTO.LoginResponse testLogin() {
        // 1. 테스트 로그인 활성화 여부 확인
        boolean isEnabled = appSettingRepository.findBySettingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)
                .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                .orElse(false);

        if (!isEnabled) {
            log.warn("테스트 로그인 시도 - 비활성화 상태");
            throw new GeneralException(ErrorStatus.AUTH_TEST_LOGIN_DISABLED);
        }

        // 2. 테스트 계정 조회 (없으면 에러)
        User user = userRepository.findByKakaoId(TEST_KAKAO_ID)
                .orElseThrow(() -> {
                    log.error("테스트 계정 없음: kakaoId={}", TEST_KAKAO_ID);
                    return new GeneralException(ErrorStatus.USER_NOT_FOUND);
                });

        // 3. 1년 만료 토큰 생성
        String accessToken = jwtProvider.createTestAccessToken(user.getId());
        String familyId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createTestRefreshToken(user.getId(), familyId);

        // 4. Refresh Token 저장
        refreshTokenSessionStore.issue(user.getId(), refreshToken, familyId,
                jwtProvider.getExpiration(refreshToken).toInstant());

        log.info("테스트 로그인 성공: userId={}", user.getId());

        // 5. 응답 생성
        return AuthConverter.toLoginResponse(
                accessToken, refreshToken, jwtProvider.getTestTokenExpiry(), user, false);
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
