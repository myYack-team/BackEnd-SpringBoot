package com.myyak.service.adminAuthService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.AdminAccessProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {
    private final AdminSessionStore store = mock(AdminSessionStore.class);
    private final AdminMailSender mail = mock(AdminMailSender.class);
    private final AdminAccessProperties properties = new AdminAccessProperties();
    private final AdminAuthServiceImpl service = new AdminAuthServiceImpl(properties, store, mail);

    @BeforeEach void configure() {
        properties.setEnabled(true); properties.setEmail("admin@example.test"); properties.setSender("sender@example.test");
    }

    @Test void storesOnlySaltedCodeHashAndIssuesOpaqueSessionAfterConsumption() {
        when(store.createChallenge(anyString(), anyString(), any())).thenReturn(true);
        String challenge = service.createChallenge();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendLoginCode(code.capture());
        assertThat(challenge).matches("[A-Za-z0-9_-]{43}");
        assertThat(code.getValue()).matches("[0-9]{9}");
        String digest = AdminAuthServiceImpl.hash(challenge + ":" + code.getValue());
        verify(store).createChallenge(challenge, digest, Duration.ofMinutes(5));
        when(store.consumeChallenge(challenge, digest)).thenReturn(true).thenReturn(false);
        String session = service.createSession(challenge, code.getValue());
        verify(store).createSession(eq(AdminAuthServiceImpl.hash(session)), anyString(), eq(Duration.ofHours(8)));
        assertThatThrownBy(() -> service.createSession(challenge, code.getValue())).isInstanceOf(GeneralException.class);
    }

    @Test void disabledConfigurationAndRateLimitNeverSendMail() {
        properties.setEnabled(false);
        assertThatThrownBy(service::createChallenge).isInstanceOf(GeneralException.class);
        properties.setEnabled(true);
        when(store.createChallenge(anyString(), anyString(), any())).thenReturn(false);
        assertThatThrownBy(service::createChallenge).isInstanceOf(GeneralException.class);
        verifyNoInteractions(mail);
    }

    @Test void invalidCookiesNeverReachStoreAndSessionCanBeRevoked() {
        assertThat(service.findSession("invalid")).isNull();
        verifyNoInteractions(store);
        String token = "x".repeat(43);
        when(store.findSession(AdminAuthServiceImpl.hash(token))).thenReturn("csrf");
        assertThat(service.findSession(token)).isEqualTo("csrf");
        service.deleteSession(token);
        verify(store).deleteSession(AdminAuthServiceImpl.hash(token));
    }
}
