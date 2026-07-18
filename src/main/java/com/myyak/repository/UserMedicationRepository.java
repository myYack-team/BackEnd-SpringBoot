package com.myyak.repository;

import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserMedicationRepository extends JpaRepository<UserMedication, Long> {

    // 사용자의 모든 약물 목록
    List<UserMedication> findByUser(User user);

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

    // 처방전 ID로 약물 목록 조회
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.prescriptionId = :prescriptionId")
    List<UserMedication> findByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    // 여러 처방전 ID로 약물 목록 조회 (DrugInfo 제외) - 목록 조회용 (성능 최적화)
    @Query("SELECT um FROM UserMedication um WHERE um.prescriptionId IN :prescriptionIds")
    List<UserMedication> findByPrescriptionIdInLight(@Param("prescriptionIds") List<Long> prescriptionIds);
}
