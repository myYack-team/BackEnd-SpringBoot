package com.myyak.config;

import com.myyak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        migrateExistingUsersConsent();
        log.info("DataInitializer 실행 완료");
    }

    /**
     * 기존 사용자들의 약관 동의 상태를 마이그레이션합니다.
     * 기존 사용자는 이미 서비스를 사용 중이므로 동의한 것으로 간주합니다.
     */
    private void migrateExistingUsersConsent() {
        int updatedCount = userRepository.updateConsentForExistingUsers();
        if (updatedCount > 0) {
            log.info("기존 사용자 약관 동의 마이그레이션 완료 - 업데이트된 사용자 수: {}", updatedCount);
        }
    }
}
