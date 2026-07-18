package com.myyak.service.oAuthService.kakaoService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.web.dto.AuthDTO.KakaoTokenResponse;
import com.myyak.web.dto.AuthDTO.KakaoUserInfo;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * 카카오 OAuth 서비스 구현체
 */
@Slf4j
@Service
public class KakaoOAuthServiceImpl implements KakaoOAuthService {

    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com";
    private static final String KAKAO_API_URL = "https://kapi.kakao.com";
    private static final Duration KAKAO_API_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient kakaoAuthClient;
    private final WebClient kakaoApiClient;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String adminKey;

    public KakaoOAuthServiceImpl(
            WebClient webClient,
            @Value("${oauth.kakao.client-id}") String clientId,
            @Value("${oauth.kakao.client-secret}") String clientSecret,
            @Value("${oauth.kakao.redirect-uri}") String redirectUri,
            @Value("${oauth.kakao.admin-key:}") String adminKey) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.adminKey = adminKey;

        this.kakaoAuthClient = webClient.mutate()
                .baseUrl(KAKAO_AUTH_URL)
                .build();

        this.kakaoApiClient = webClient.mutate()
                .baseUrl(KAKAO_API_URL)
                .build();
    }

    @Override
    public String getAuthorizationUrl(String baseUrl, String state) {
        String dynamicRedirectUri = buildRedirectUri(baseUrl);
        log.debug("동적 redirect_uri 생성: {}, state: {}", dynamicRedirectUri, state);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(KAKAO_AUTH_URL + "/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", dynamicRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "profile_nickname,profile_image,account_email");

        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }

        return builder.build().toUriString();
    }

    /**
     * 요청 base URL을 기반으로 redirect_uri 생성
     */
    private String buildRedirectUri(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + "/api/auth/kakao/callback";
        }
        return redirectUri; // fallback to configured value
    }

    @Override
    public KakaoTokenResponse exchangeCodeForToken(String code, String baseUrl) {
        log.debug("카카오 인가 코드로 토큰 교환 시작");

        String dynamicRedirectUri = buildRedirectUri(baseUrl);
        log.debug("토큰 교환 redirect_uri: {}", dynamicRedirectUri);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("redirect_uri", dynamicRedirectUri);
        formData.add("code", code);
        if (clientSecret != null && !clientSecret.equals("dev-client-secret")) {
            formData.add("client_secret", clientSecret);
        }

        try {
            KakaoTokenResponse tokenResponse = kakaoAuthClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .doOnNext(body -> log.error("카카오 토큰 교환 4xx 에러: {}, 응답: {}", response.statusCode(), body))
                                .then(Mono.error(new GeneralException(ErrorStatus.AUTH_INVALID_CODE)))
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("카카오 토큰 교환 5xx 에러: {}", response.statusCode());
                        return Mono.error(new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED));
                    })
                    .bodyToMono(KakaoTokenResponse.class)
                    .block(KAKAO_API_TIMEOUT);

            log.info("카카오 토큰 교환 성공");
            return tokenResponse;

        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 토큰 교환 실패: {}", e.getMessage());
            throw new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED);
        }
    }

    @Override
    public KakaoUserInfo getUserInfo(String accessToken) {
        log.debug("카카오 사용자 정보 조회 시작");

        try {
            KakaoUserInfo userInfo = kakaoApiClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        log.error("카카오 API 4xx 에러: {}", response.statusCode());
                        return Mono.error(new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response -> {
                        log.error("카카오 API 5xx 에러: {}", response.statusCode());
                        return Mono.error(new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED));
                    })
                    .bodyToMono(KakaoUserInfo.class)
                    .block(KAKAO_API_TIMEOUT);

            log.info("카카오 사용자 정보 조회 성공: kakaoId={}", userInfo != null ? userInfo.getId() : null);
            log.debug("카카오 사용자 정보: {}", userInfo);

            return userInfo;
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new GeneralException(ErrorStatus.AUTH_KAKAO_LOGIN_FAILED);
        }
    }

    @Override
    public Long unlinkUser(String accessToken) {
        log.info("카카오 연결 끊기 시작");

        try {
            // 카카오 연결 끊기 API 응답: {"id": 123456789}
            var response = kakaoApiClient.post()
                    .uri("/v1/user/unlink")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        log.warn("카카오 연결 끊기 4xx 에러: {} (토큰 만료 또는 이미 연결 해제됨)", clientResponse.statusCode());
                        return Mono.empty(); // 4xx 에러는 무시 (이미 연결 해제된 경우 등)
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        log.error("카카오 연결 끊기 5xx 에러: {}", clientResponse.statusCode());
                        return Mono.empty(); // 5xx 에러도 무시 (탈퇴 자체는 진행)
                    })
                    .bodyToMono(java.util.Map.class)
                    .block(KAKAO_API_TIMEOUT);

            if (response != null && response.containsKey("id")) {
                Long kakaoId = ((Number) response.get("id")).longValue();
                log.info("카카오 연결 끊기 성공: kakaoId={}", kakaoId);
                return kakaoId;
            }

            log.warn("카카오 연결 끊기 응답 없음 (이미 연결 해제되었을 수 있음)");
            return null;

        } catch (Exception e) {
            // 연결 끊기 실패해도 탈퇴는 진행되어야 함
            log.error("카카오 연결 끊기 실패 (탈퇴는 계속 진행): {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Long unlinkUserByAdmin(String kakaoId) {
        log.info("카카오 연결 끊기 (Admin API) 시작 - kakaoId: {}", kakaoId);

        if (adminKey == null || adminKey.isBlank()) {
            log.warn("카카오 Admin Key가 설정되지 않음. 연결 끊기를 건너뜁니다.");
            return null;
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("target_id_type", "user_id");
            formData.add("target_id", kakaoId);

            var response = kakaoApiClient.post()
                    .uri("/v1/user/unlink")
                    .header("Authorization", "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        log.warn("카카오 연결 끊기 (Admin) 4xx 에러: {} (이미 연결 해제됨)", clientResponse.statusCode());
                        return Mono.empty();
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        log.error("카카오 연결 끊기 (Admin) 5xx 에러: {}", clientResponse.statusCode());
                        return Mono.empty();
                    })
                    .bodyToMono(java.util.Map.class)
                    .block(KAKAO_API_TIMEOUT);

            if (response != null && response.containsKey("id")) {
                Long unlinkedKakaoId = ((Number) response.get("id")).longValue();
                log.info("카카오 연결 끊기 (Admin) 성공: kakaoId={}", unlinkedKakaoId);
                return unlinkedKakaoId;
            }

            log.warn("카카오 연결 끊기 (Admin) 응답 없음");
            return null;

        } catch (Exception e) {
            log.error("카카오 연결 끊기 (Admin) 실패 (탈퇴는 계속 진행): {}", e.getMessage());
            return null;
        }
    }
}
