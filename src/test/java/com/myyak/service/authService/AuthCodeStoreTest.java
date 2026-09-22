package com.myyak.service.authService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.service.authService.store.TemporaryAuthStore;
import com.myyak.service.authService.store.TemporaryAuthStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthCodeStoreTest {

    @Mock
    private TemporaryAuthStore temporaryAuthStore;

    private ObjectMapper objectMapper;
    private AuthCodeStore authCodeStore;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        authCodeStore = new AuthCodeStore(temporaryAuthStore, objectMapper);
    }

    @Test
    void storesCodeEntryAsJsonWithFiveMinuteTtl() throws Exception {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        String code = authCodeStore.createCode("access", "refresh", true, null, false);

        verify(temporaryAuthStore).put(
                eq(AuthCodeStore.KEY_PREFIX + code),
                jsonCaptor.capture(),
                eq(AuthCodeStore.CODE_TTL)
        );
        AuthCodeStore.CodeEntry stored = objectMapper.readValue(jsonCaptor.getValue(), AuthCodeStore.CodeEntry.class);
        assertThat(stored.accessToken()).isEqualTo("access");
        assertThat(stored.refreshToken()).isEqualTo("refresh");
        assertThat(stored.isNewUser()).isTrue();
        assertThat(stored.termsAgreed()).isNull();
        assertThat(stored.privacyAgreed()).isFalse();
        assertThat(stored.createdAt()).isNotNull();
    }

    @Test
    void restoresNullableConsentAndTimestampWhenCodeIsConsumed() throws Exception {
        Instant createdAt = Instant.parse("2026-09-22T01:02:03.456Z");
        AuthCodeStore.CodeEntry expected = new AuthCodeStore.CodeEntry(
                "access", "refresh", false, null, true, createdAt
        );
        when(temporaryAuthStore.consume(AuthCodeStore.KEY_PREFIX + "code"))
                .thenReturn(objectMapper.writeValueAsString(expected));

        AuthCodeStore.CodeEntry actual = authCodeStore.exchangeCode("code");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void corruptJsonIsReportedAsTemporaryAuthStoreFailure() {
        when(temporaryAuthStore.consume(AuthCodeStore.KEY_PREFIX + "code")).thenReturn("not-json");

        assertThatThrownBy(() -> authCodeStore.exchangeCode("code"))
                .isInstanceOf(TemporaryAuthStoreException.class)
                .satisfies(error -> assertThat(((TemporaryAuthStoreException) error).getCode())
                        .isEqualTo(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE));
    }

    @Test
    void rejectsBlankCodeWithoutCallingRedis() {
        assertThat(authCodeStore.exchangeCode(" ")).isNull();
        verifyNoInteractions(temporaryAuthStore);
    }
}
