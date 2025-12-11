package com.myyak.config;

import com.myyak.domain.User;
import com.myyak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        // 테스트용 사용자가 없으면 생성
        if (!userRepository.existsById(1L)) {
            User testUser = User.builder()
                    .kakaoId("test_user_001")
                    .name("테스트 사용자")
                    .build();

            userRepository.save(testUser);
            log.info("테스트 사용자 생성 완료 (ID: {})", testUser.getId());
        } else {
            log.info("테스트 사용자가 이미 존재합니다.");
        }
    }
}
