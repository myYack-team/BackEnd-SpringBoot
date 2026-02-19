package com.myyak.repository;

import com.myyak.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

    boolean existsByKakaoId(String kakaoId);

    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 이름, 이메일, 카카오ID로 검색
     */
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "u.kakaoId LIKE CONCAT('%', :keyword, '%')")
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 기존 사용자의 약관 동의 상태 마이그레이션
     * termsAgreed와 privacyAgreed가 false인 기존 사용자를 true로 업데이트
     */
    @Modifying
    @Query("UPDATE User u SET u.termsAgreed = true, u.privacyAgreed = true " +
            "WHERE u.termsAgreed = false AND u.privacyAgreed = false")
    int updateConsentForExistingUsers();

    /**
     * 전화번호 해시로 사용자 조회
     * (암호화된 phone 필드 대신 phoneHash 사용)
     */
    Optional<User> findByPhoneHash(String phoneHash);

    /**
     * 전화번호 해시 존재 여부 확인
     */
    boolean existsByPhoneHash(String phoneHash);
}
