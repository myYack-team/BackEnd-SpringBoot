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
    @Query("SELECT um FROM UserMedication um WHERE um.user = :user AND um.isActive = true AND um.remainingCount > 0")
    List<UserMedication> findActiveMedicationsWithStock(@Param("user") User user);

    // 사용자 ID로 활성 약물 조회
    @Query("SELECT um FROM UserMedication um WHERE um.user.id = :userId AND um.isActive = true")
    List<UserMedication> findActiveByUserId(@Param("userId") Long userId);

    // DrugInfo와 함께 조회 (N+1 방지)
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.user = :user AND um.isActive = true")
    List<UserMedication> findByUserWithDrugInfo(@Param("user") User user);

    // 특정 DrugInfo를 사용하는 사용자의 약물
    @Query("SELECT um FROM UserMedication um WHERE um.user = :user AND um.drugInfo.itemSeq = :itemSeq AND um.isActive = true")
    Optional<UserMedication> findByUserAndDrugItemSeq(@Param("user") User user, @Param("itemSeq") String itemSeq);

    // 처방전 ID로 약물 목록 조회
    @Query("SELECT um FROM UserMedication um LEFT JOIN FETCH um.drugInfo WHERE um.prescriptionId = :prescriptionId")
    List<UserMedication> findByPrescriptionId(@Param("prescriptionId") Long prescriptionId);

    // 처방전 ID로 약물 개수 조회
    @Query("SELECT COUNT(um) FROM UserMedication um WHERE um.prescriptionId = :prescriptionId")
    int countByPrescriptionId(@Param("prescriptionId") Long prescriptionId);
}
