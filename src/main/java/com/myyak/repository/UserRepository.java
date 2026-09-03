package com.myyak.repository;

import com.myyak.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

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
     * 특정 시점 이후 가입한 사용자 수 조회
     */
    long countByCreatedAtAfter(LocalDateTime dateTime);

    /**
     * 성별 분포 집계
     */
    @Query("SELECT u.gender, COUNT(u) FROM User u GROUP BY u.gender")
    List<Object[]> countGroupByGender();

    /**
     * 연령대 분포 집계
     */
    @Query("SELECT u.ageRange, COUNT(u) FROM User u GROUP BY u.ageRange")
    List<Object[]> countGroupByAgeRange();

    /**
     * 가입목적 컬럼만 조회 (콤마 구분 문자열이라 집계는 메모리에서 처리)
     */
    @Query("SELECT u.signupPurposes FROM User u WHERE u.signupPurposes IS NOT NULL")
    List<String> findAllSignupPurposes();

    /**
     * 일자별 가입자 수 집계 - Native Query
     */
    @Query(value = "SELECT DATE(created_at) AS signup_date, COUNT(*) AS signup_count " +
            "FROM users WHERE created_at >= :since " +
            "GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> countDailySignups(@Param("since") LocalDateTime since);

    /**
     * 활성 리마인더를 보유한 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT u.id) FROM User u WHERE u.id IN " +
           "(SELECT um.user.id FROM Reminder r JOIN r.userMedication um WHERE r.enabled = true AND um.isActive = true) " +
           "OR u.id IN (SELECT us.user.id FROM Reminder r2 JOIN r2.userSupplement us WHERE r2.enabled = true AND us.isActive = true)")
    long countUsersWithActiveReminder();

    /**
     * 활성 리마인더를 보유하고 FCM 토큰이 등록된 사용자 수 (푸시 도달 가능)
     */
    @Query("SELECT COUNT(DISTINCT u.id) FROM User u WHERE u.fcmToken IS NOT NULL AND (u.id IN " +
           "(SELECT um.user.id FROM Reminder r JOIN r.userMedication um WHERE r.enabled = true AND um.isActive = true) " +
           "OR u.id IN (SELECT us.user.id FROM Reminder r2 JOIN r2.userSupplement us WHERE r2.enabled = true AND us.isActive = true))")
    long countPushReachableUsersWithActiveReminder();
}
