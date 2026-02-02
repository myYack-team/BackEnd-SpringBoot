package com.myyak.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 전화번호 해시 유틸리티
 * 전화번호 검색용 SHA-256 해시 생성 (salt 사용)
 */
@Slf4j
@Component
public class PhoneHashUtil {

    private final String salt;

    public PhoneHashUtil(@Value("${encryption.phone-hash-salt:myyak-phone-salt-2024}") String salt) {
        this.salt = salt;
    }

    /**
     * 전화번호를 SHA-256 해시로 변환
     * @param phone 전화번호 (01012345678 형식)
     * @return Base64 인코딩된 해시값
     */
    public String hash(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }

        try {
            // 전화번호 정규화 (숫자만 추출)
            String normalizedPhone = phone.replaceAll("[^0-9]", "");

            // salt + phone 조합
            String dataToHash = salt + normalizedPhone;

            // SHA-256 해시 생성
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));

            // Base64 인코딩하여 반환
            return Base64.getEncoder().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 알고리즘을 찾을 수 없습니다.", e);
            throw new RuntimeException("해시 처리 중 오류가 발생했습니다.", e);
        }
    }
}
