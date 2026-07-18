package com.myyak.repository;

import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserMedicationRepository extends JpaRepository<UserMedication, Long> {

    // 사용자의 활성 약물 목록
    List<UserMedication> findByUserAndIsActiveTrue(User user);

    // 사용자의 모든 약물 목록
    List<UserMedication> findByUser(User user);

    // 사용자의 특정 약물
    Optional<UserMedication> findByIdAndUser(Long id, User user);

    // 재고가 있는 활성 약물
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.user = :user " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)")
    List<UserMedication> findActiveMedicationsWithStock(@Param("user") User user);

    // 사용자 ID로 활성 약물 조회
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.user.id = :userId " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)")
    List<UserMedication> findActiveByUserId(@Param("userId") Long userId);

    // DrugInfo와 함께 조회 (N+1 방지) - 전체 필드 (상세 조회용)
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo " +
            "WHERE um.user = :user " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)")
    List<UserMedication> findByUserWithDrugInfo(@Param("user") User user);

    // DrugInfo 없이 조회 (목록용) - DrugInfo는 별도 경량 쿼리로 조회
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.user = :user " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)")
    List<UserMedication> findByUserActiveOnly(@Param("user") User user);

    // 특정 DrugInfo를 사용하는 사용자의 약물
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.user = :user " +
            "AND um.drugInfo.itemSeq = :itemSeq " +
            "AND um.isActive = true " +
            "AND um.remainingCount > 0 " +
            "AND (um.endDate IS NULL OR um.endDate > CURRENT_DATE)")
    Optional<UserMedication> findByUserAndDrugItemSeq(@Param("user") User user, @Param("itemSeq") String itemSeq);

    // 처방전 ID로 약물 목록 조회
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.prescriptionId = :prescriptionId")
    List<UserMedication> findByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    // 처방전 ID로 약물 개수 조회
    @Query("SELECT COUNT(um) FROM UserMedication um WHERE um.prescriptionId = :prescriptionId")
    int countByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    // 여러 처방전 ID로 약물 목록 한 번에 조회 (N+1 방지) - 상세 조회용
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.prescriptionId IN :prescriptionIds")
    List<UserMedication> findByPrescriptionIdIn(@Param("prescriptionIds") List<Long> prescriptionIds);

    // 여러 처방전 ID로 약물 목록 조회 (DrugInfo 제외) - 목록 조회용 (성능 최적화)
    @Query("SELECT um FROM UserMedication um WHERE um.prescriptionId IN :prescriptionIds")
    List<UserMedication> findByPrescriptionIdInLight(@Param("prescriptionIds") List<Long> prescriptionIds);

    // 일괄 삭제용: 사용자 소유의 활성 약물만 조회 (소유권 검증을 쿼리 조건으로 수행)
    @Query("SELECT um FROM UserMedication um " +
            "WHERE um.id IN :ids " +
            "AND um.user.id = :userId " +
            "AND um.isActive = true")
    List<UserMedication> findActiveByIdInAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
