package com.myyak.service.authService;

import com.myyak.repository.AppSettingRepository;
import com.myyak.service.authService.store.RefreshTokenMigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "auth.refresh-store.mode=redis",
        "auth.refresh-store.migrate-legacy=true"
})
@Import(RefreshTokenMigrationState.class)
class RefreshTokenMigrationStateIntegrationTest {

    @Autowired private RefreshTokenMigrationState state;
    @Autowired private AppSettingRepository settings;

    @Test
    void persistsOneTimeMarkerAcrossTransactions() {
        assertThat(state.begin()).isTrue();
        assertThatThrownBy(state::begin).isInstanceOf(IllegalStateException.class);

        state.complete();

        assertThat(state.begin()).isFalse();
        assertThat(settings.findBySettingKey("auth.refresh-store.legacy-migration-state"))
                .get().extracting("settingValue").isEqualTo("COMPLETE");
    }
}
