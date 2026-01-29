package com.myyak.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 암호화 유틸리티
 * 민감한 개인정보를 DB에 저장할 때 사용
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionUtil(@Value("${encryption.secret-key}") String secretKeyBase64) {
        byte[] decodedKey = Base64.getDecoder().decode(secretKeyBase64);
        if (decodedKey.length != 32) {
            throw new IllegalArgumentException("암호화 키는 32바이트(256비트)여야 합니다.");
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * 평문을 AES-256-GCM으로 암호화
     * @param plainText 평문
     * @return Base64 인코딩된 암호문 (IV + 암호문)
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // IV 생성
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // 암호화
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));

            // IV + 암호문 결합
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Base64 인코딩
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("암호화 실패", e);
            throw new RuntimeException("암호화 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * AES-256-GCM 암호문을 복호화
     * 평문 데이터(암호화되지 않은 기존 데이터)는 그대로 반환하여 마이그레이션 호환성 유지
     *
     * @param encryptedText Base64 인코딩된 암호문 또는 평문
     * @return 복호화된 평문 또는 원본 평문
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        // 암호화된 데이터는 항상 Base64 형식 (IV 12바이트 + 암호문)
        // 최소 길이: 12(IV) + 16(tag) = 28바이트 → Base64로 약 40자 이상
        // 평문 데이터인지 먼저 확인
        if (!isLikelyEncrypted(encryptedText)) {
            log.debug("평문 데이터 감지, 원본 반환");
            return encryptedText;
        }

        try {
            // Base64 디코딩
            byte[] encryptedData = Base64.getDecoder().decode(encryptedText);

            // IV와 암호문 분리
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // 복호화
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, "UTF-8");

        } catch (IllegalArgumentException e) {
            // Base64 디코딩 실패 → 평문 데이터로 간주
            log.warn("Base64 디코딩 실패, 평문으로 간주: {}", e.getMessage());
            return encryptedText;
        } catch (Exception e) {
            // 복호화 실패 → 평문 데이터일 가능성
            log.warn("복호화 실패, 평문으로 간주: {}", e.getMessage());
            return encryptedText;
        }
    }

    /**
     * 데이터가 암호화된 형식인지 간단히 판별
     * - 암호화된 데이터는 Base64 문자만 포함
     * - 평문 이메일은 @ 또는 . 등의 특수문자 포함
     */
    private boolean isLikelyEncrypted(String data) {
        // 너무 짧으면 암호화된 데이터가 아님 (최소 IV + tag = 28바이트 → Base64 약 40자)
        if (data.length() < 40) {
            return false;
        }
        // @ 포함 시 이메일 평문으로 간주
        if (data.contains("@")) {
            return false;
        }
        // Base64 문자셋 검증 (A-Z, a-z, 0-9, +, /, =)
        return data.matches("^[A-Za-z0-9+/=]+$");
    }
}
