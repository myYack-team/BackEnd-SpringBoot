package com.myyak.service.authService;

import com.myyak.service.authService.store.TemporaryAuthStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthStateStoreTest {

    @Mock
    private TemporaryAuthStore temporaryAuthStore;

    private OAuthStateStore oAuthStateStore;

    @BeforeEach
    void setUp() {
        oAuthStateStore = new OAuthStateStore(temporaryAuthStore);
    }

    @Test
    void createsStateWithTenMinuteTtl() {
        String redirectUri = "myyak://oauth/callback";

        String state = oAuthStateStore.createState(redirectUri);

        assertThat(state).isNotBlank();
        verify(temporaryAuthStore).put(
                eq(OAuthStateStore.KEY_PREFIX + state),
                eq(redirectUri),
                eq(OAuthStateStore.STATE_TTL)
        );
    }

    @Test
    void consumesStateOnlyThroughAtomicStoreOperation() {
        String state = "state-token";
        String redirectUri = "myyak://oauth/callback";
        when(temporaryAuthStore.consume(OAuthStateStore.KEY_PREFIX + state))
                .thenReturn(redirectUri)
                .thenReturn(null);

        assertThat(oAuthStateStore.validateAndConsume(state)).isEqualTo(redirectUri);
        assertThat(oAuthStateStore.validateAndConsume(state)).isNull();
        verify(temporaryAuthStore, never()).put(eq(OAuthStateStore.KEY_PREFIX + state), eq(redirectUri), eq(OAuthStateStore.STATE_TTL));
    }

    @Test
    void rejectsBlankStateWithoutCallingRedis() {
        assertThat(oAuthStateStore.validateAndConsume(" ")).isNull();
        verifyNoInteractions(temporaryAuthStore);
    }
}
