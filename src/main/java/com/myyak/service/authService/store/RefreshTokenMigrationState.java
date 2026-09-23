package com.myyak.service.authService.store;

import com.myyak.domain.AppSetting;
import com.myyak.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable one-time guard so a restart cannot resurrect revoked RDS tokens. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.refresh-store.mode", havingValue = "redis")
@ConditionalOnProperty(name = "auth.refresh-store.migrate-legacy", havingValue = "true")
public class RefreshTokenMigrationState {

    static final String KEY = "auth.refresh-store.legacy-migration-state";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETE = "COMPLETE";

    private final AppSettingRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean begin() {
        AppSetting existing = repository.findBySettingKey(KEY).orElse(null);
        if (existing != null) {
            if (COMPLETE.equals(existing.getSettingValue())) return false;
            throw new IllegalStateException("Refresh Token migration requires manual recovery before restart");
        }
        repository.saveAndFlush(AppSetting.builder()
                .settingKey(KEY)
                .settingValue(IN_PROGRESS)
                .description("One-time RDS to Redis Refresh Token migration")
                .build());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete() {
        AppSetting state = repository.findBySettingKey(KEY)
                .orElseThrow(() -> new IllegalStateException("Refresh Token migration state is missing"));
        if (!IN_PROGRESS.equals(state.getSettingValue())) {
            throw new IllegalStateException("Unexpected Refresh Token migration state");
        }
        state.updateValue(COMPLETE);
    }
}
