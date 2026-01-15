package com.myyak.repository;

import com.myyak.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

    boolean existsByKakaoId(String kakaoId);

    /**
     * 이름, 이메일, 카카오ID로 검색
     */
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "u.kakaoId LIKE CONCAT('%', :keyword, '%')")
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
