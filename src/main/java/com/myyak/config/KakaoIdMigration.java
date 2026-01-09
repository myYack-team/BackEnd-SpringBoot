package com.myyak.config;

import com.myyak.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * kakaoId 암호화 해제 마이그레이션
 * 기존 암호화된 kakaoId를 평문으로 변환합니다.
 * 한 번 실행 후 이 클래스는 삭제해도 됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoIdMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionUtil encryptionUtil;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== kakaoId 마이그레이션 시작 ===");

        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, kakao_id FROM users WHERE kakao_id IS NOT NULL"
            );

            int migrated = 0;
            int skipped = 0;

            for (Map<String, Object> user : users) {
                Long id = ((Number) user.get("id")).longValue();
                String kakaoId = (String) user.get("kakao_id");

                // 이미 평문인지 확인 (숫자로만 구성된 경우)
                if (kakaoId.matches("\\d+")) {
                    log.debug("이미 평문 kakaoId: userId={}", id);
                    skipped++;
                    continue;
                }

                try {
                    // 암호화된 값 복호화
                    String decrypted = encryptionUtil.decrypt(kakaoId);

                    // 업데이트
                    jdbcTemplate.update(
                            "UPDATE users SET kakao_id = ? WHERE id = ?",
                            decrypted, id
                    );

                    log.info("kakaoId 복호화 완료: userId={}", id);
                    migrated++;
                } catch (Exception e) {
                    log.warn("kakaoId 복호화 실패 (이미 평문일 수 있음): userId={}, error={}", id, e.getMessage());
                    skipped++;
                }
            }

            log.info("=== kakaoId 마이그레이션 완료: migrated={}, skipped={} ===", migrated, skipped);
        } catch (Exception e) {
            log.error("kakaoId 마이그레이션 실패", e);
        }
    }
}
