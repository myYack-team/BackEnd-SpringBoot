package com.myyak.service.authService;

import com.myyak.domain.AppSetting;
import com.myyak.repository.AppSettingRepository;
import com.myyak.repository.RefreshTokenRepository;
import com.myyak.service.authService.store.RedisRefreshTokenSessionStore;
import com.myyak.service.authService.store.RefreshTokenMigrationRunner;
import com.myyak.service.authService.store.RefreshTokenMigrationState;
import com.myyak.util.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshTokenMigrationTest {

    @Test
    void completedMigrationCannotImportRevokedRdsTokensAgain() {
        AppSettingRepository settings = mock(AppSettingRepository.class);
        when(settings.findBySettingKey(anyString())).thenReturn(Optional.of(AppSetting.builder()
                .settingValue("COMPLETE").build()));
        RefreshTokenMigrationState state = new RefreshTokenMigrationState(settings);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        RedisRefreshTokenSessionStore store = mock(RedisRefreshTokenSessionStore.class);
        RefreshTokenMigrationRunner runner = new RefreshTokenMigrationRunner(
                tokens, store, mock(JwtProvider.class), state);

        assertThat(state.begin()).isFalse();
        runner.run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(tokens, store);
    }

    @Test
    void interruptedMigrationFailsClosedUntilOperatorRecovery() {
        AppSettingRepository settings = mock(AppSettingRepository.class);
        when(settings.findBySettingKey(anyString())).thenReturn(Optional.of(AppSetting.builder()
                .settingValue("IN_PROGRESS").build()));
        RefreshTokenMigrationState state = new RefreshTokenMigrationState(settings);

        assertThatThrownBy(state::begin).isInstanceOf(IllegalStateException.class);
        verify(settings, never()).saveAndFlush(any(AppSetting.class));
    }
}
