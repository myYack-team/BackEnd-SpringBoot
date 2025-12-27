package com.myyak.web.dto.AuthDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 카카오 토큰 응답 DTO
 * https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#request-token-response
 */
@Getter
@NoArgsConstructor
@ToString
public class KakaoTokenResponse {

    @JsonProperty("token_type")
    private String tokenType;  // bearer

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("id_token")
    private String idToken;  // OIDC 사용 시

    @JsonProperty("expires_in")
    private Integer expiresIn;  // 액세스 토큰 만료 시간 (초)

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("refresh_token_expires_in")
    private Integer refreshTokenExpiresIn;  // 리프레시 토큰 만료 시간 (초)

    private String scope;
}
