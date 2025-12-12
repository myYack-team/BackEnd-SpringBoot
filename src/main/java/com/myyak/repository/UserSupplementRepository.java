package com.myyak.repository;

import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.UserSupplement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSupplementRepository extends JpaRepository<UserSupplement, Long> {

    // 사용자의 활성 영양제 목록
    List<UserSupplement> findByUserAndIsActiveTrue(User user);

    // 사용자의 모든 영양제 목록
    List<UserSupplement> findByUser(User user);

    // 사용자의 특정 영양제
    Optional<UserSupplement> findByIdAndUser(Long id, User user);

    // 사용자 ID로 활성 영양제 조회
    @Query("SELECT us FROM UserSupplement us WHERE us.user.id = :userId AND us.isActive = true")
    List<UserSupplement> findActiveByUserId(@Param("userId") Long userId);

    // Supplement와 함께 조회 (N+1 방지)
    @Query("SELECT us FROM UserSupplement us LEFT JOIN FETCH us.supplement WHERE us.user = :user AND us.isActive = true")
    List<UserSupplement> findByUserWithSupplement(@Param("user") User user);

    // 특정 Supplement를 사용하는 사용자의 영양제
    @Query("SELECT us FROM UserSupplement us WHERE us.user = :user AND us.supplement.id = :supplementId AND us.isActive = true")
    Optional<UserSupplement> findByUserAndSupplementId(@Param("user") User user, @Param("supplementId") Long supplementId);

    // 특정 영양제를 선택한 사용자 수
    @Query("SELECT COUNT(us) FROM UserSupplement us WHERE us.supplement = :supplement AND us.isActive = true")
    int countActiveUsersBySupplement(@Param("supplement") Supplement supplement);
}
