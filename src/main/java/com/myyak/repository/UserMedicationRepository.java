package com.myyak.repository;

import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserMedicationRepository extends JpaRepository<UserMedication, Long> {

    // 사용자의 모든 약물 목록
    List<UserMedication> findByUser(User user);

    // DrugInfo와 함께 조회 (N+1 방지) - 전체 필드 (상세 조회용)
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo " +
            "WHERE um.user = :user " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > :today)")
    List<UserMedication> findByUserWithDrugInfo(@Param("user") User user, @Param("today") LocalDate today);

    // DrugInfo 없이 조회 (목록용) - DrugInfo는 별도 경량 쿼리로 조회
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.user = :user " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > :today)")
    List<UserMedication> findByUserActiveOnly(@Param("user") User user, @Param("today") LocalDate today);

    // 처방전 ID로 약물 목록 조회
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.prescriptionId = :prescriptionId")
    List<UserMedication> findByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    // 여러 처방전 ID로 약물 목록 조회 (DrugInfo 제외) - 목록 조회용 (성능 최적화)
    @Query("SELECT um FROM UserMedication um WHERE um.prescriptionId IN :prescriptionIds")
    List<UserMedication> findByPrescriptionIdInLight(@Param("prescriptionIds") List<Long> prescriptionIds);

    // 일괄 삭제용: 사용자 소유의 활성 약물만 조회 (소유권 검증을 쿼리 조건으로 수행)
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.id IN :ids " +
            "AND um.user.id = :userId " +
            "AND um.isActive = true")
    List<UserMedication> findActiveByIdInAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    // 여러 사용자의 약물 수 한 번에 집계 (N+1 방지)
    @Query("SELECT um.user.id, COUNT(um) FROM UserMedication um WHERE um.user.id IN :userIds GROUP BY um.user.id")
    List<Object[]> countByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 회원 탈퇴 시 사용자의 모든 약물 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM UserMedication m WHERE m.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
