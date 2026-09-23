package com.myyak.service.authService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.util.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderRefreshTokenTest {

    @Test
    void refreshTokensIssuedInSameSecondAreUniqueAndKeepFamily() {
        JwtProvider provider = provider(60_000L);
        String first = provider.createRefreshToken(42L, "family-1");
        String second = provider.createRefreshToken(42L, "family-1");

        assertThat(first).isNotEqualTo(second);
        assertThat(provider.validateAndParseRefreshToken(first).get("family", String.class))
                .isEqualTo("family-1");
        assertThat(provider.validateAndParseRefreshToken(first).get("type", String.class))
                .isEqualTo("refresh");
    }

    @Test
    void expiredRefreshTokenIsRejectedBeforeStoreLookup() {
        JwtProvider provider = provider(-60_000L);
        String token = provider.createRefreshToken(42L, "family-1");

        assertThatThrownBy(() -> provider.validateAndParseRefreshToken(token))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getCode())
                        .isEqualTo(ErrorStatus.AUTH_EXPIRED_TOKEN));
    }

    private static JwtProvider provider(long refreshExpiry) {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "secretKey", "test-only-jwt-signing-secret-32-characters-minimum");
        ReflectionTestUtils.setField(provider, "refreshTokenExpiry", refreshExpiry);
        provider.init();
        return provider;
    }
}
