package com.myyak.service.authService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.AppSetting;
import com.myyak.domain.User;
import com.myyak.repository.AppSettingRepository;
import com.myyak.repository.UserRepository;
import com.myyak.service.authService.store.RefreshTokenSessionStore;
import com.myyak.service.oAuthService.kakaoService.KakaoOAuthService;
import com.myyak.service.visitService.VisitTrackingService;
import com.myyak.util.JwtProvider;
import com.myyak.web.dto.AuthDTO.AuthRequestDTO;
import com.myyak.web.dto.AuthDTO.KakaoUserInfo;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthActivityTest {
    final KakaoOAuthService kakao = mock(KakaoOAuthService.class);
    final UserRepository users = mock(UserRepository.class);
    final RefreshTokenSessionStore tokens = mock(RefreshTokenSessionStore.class);
    final JwtProvider jwt = mock(JwtProvider.class);
    final AppSettingRepository settings = mock(AppSettingRepository.class);
    final VisitTrackingService tracking = mock(VisitTrackingService.class);
    final AuthServiceImpl service = new AuthServiceImpl(kakao, users, tokens, jwt, settings, tracking);
    final User user = User.builder().id(7L).kakaoId("123").name("fixture-user").build();
    final Date expiration = Date.from(Instant.parse("2026-11-01T00:00:00Z"));

    AuthRequestDTO.RefreshRequest refreshRequest() {
        var request = mock(AuthRequestDTO.RefreshRequest.class);
        when(request.getRefreshToken()).thenReturn("fixture-old-refresh");
        var claims = Jwts.claims().setSubject("7").setExpiration(expiration);
        claims.put("type", "refresh");
        when(jwt.validateAndParseRefreshToken("fixture-old-refresh")).thenReturn(claims);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(tokens.currentFamily(7L)).thenReturn("fixture-family");
        when(jwt.createAccessToken(7L)).thenReturn("fixture-access");
        when(jwt.createRefreshToken(7L, "fixture-family")).thenReturn("fixture-new-refresh");
        when(jwt.getExpiration("fixture-new-refresh")).thenReturn(expiration);
        return request;
    }

    @Test void successfulRefreshSchedulesActivityOnlyAfterSessionRotation() {
        var result = service.refreshToken(refreshRequest());
        assertThat(result.getAccessToken()).isEqualTo("fixture-access");
        var order = inOrder(tokens, tracking);
        order.verify(tokens).currentFamily(7L);
        order.verify(tokens).rotate(7L, "fixture-old-refresh", "fixture-new-refresh", "fixture-family",
                expiration.toInstant(), expiration.toInstant());
        order.verify(tracking).recordVisitAfterCommit(7L);
    }

    @Test void invalidRefreshOrFailedSessionRotationDoesNotRecordActivity() {
        var request = refreshRequest();
        doThrow(new GeneralException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN)).when(tokens)
                .rotate(anyLong(), anyString(), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> service.refreshToken(request)).isInstanceOf(GeneralException.class);
        verifyNoInteractions(tracking);
    }

    @Test void kakaoLoginSchedulesActivityAfterIssuingTheSession() {
        var request = mock(AuthRequestDTO.KakaoLoginRequest.class);
        when(request.getAccessToken()).thenReturn("fixture-kakao");
        var info = mock(KakaoUserInfo.class);
        when(info.getId()).thenReturn(123L);
        when(kakao.getUserInfo("fixture-kakao")).thenReturn(info);
        when(users.findByKakaoId("123")).thenReturn(Optional.of(user));
        when(jwt.createAccessToken(7L)).thenReturn("fixture-access");
        when(jwt.createRefreshToken(eq(7L), anyString())).thenReturn("fixture-new-refresh");
        when(jwt.getExpiration("fixture-new-refresh")).thenReturn(expiration);
        assertThat(service.loginWithKakao(request).getUser().getId()).isEqualTo(7L);
        var order = inOrder(tokens, tracking);
        order.verify(tokens).issue(eq(7L), eq("fixture-new-refresh"), anyString(), eq(expiration.toInstant()));
        order.verify(tracking).recordVisitAfterCommit(7L);
    }

    @Test void successfulTestLoginIsIncludedButDisabledTestLoginIsNot() {
        when(settings.findBySettingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)).thenReturn(Optional.empty());
        assertThatThrownBy(service::testLogin).isInstanceOf(GeneralException.class);
        verifyNoInteractions(tracking);
        when(settings.findBySettingKey(AppSetting.KEY_TEST_LOGIN_ENABLED)).thenReturn(Optional.of(
                AppSetting.builder().settingKey(AppSetting.KEY_TEST_LOGIN_ENABLED).settingValue("true").build()));
        when(users.findByKakaoId(AuthServiceImpl.TEST_KAKAO_ID)).thenReturn(Optional.of(user));
        when(jwt.createTestAccessToken(7L)).thenReturn("fixture-test-access");
        when(jwt.createTestRefreshToken(eq(7L), anyString())).thenReturn("fixture-test-refresh");
        when(jwt.getExpiration("fixture-test-refresh")).thenReturn(expiration);
        assertThat(service.testLogin().getAccessToken()).isEqualTo("fixture-test-access");
        verify(tracking).recordVisitAfterCommit(7L);
    }

    @Test void logoutDoesNotRecordActivity() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        service.logout(7L);
        verify(tokens).revoke(7L);
        verifyNoInteractions(tracking);
    }
}
