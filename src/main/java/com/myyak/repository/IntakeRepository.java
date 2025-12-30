package com.myyak.repository;

import com.myyak.domain.Intake;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IntakeRepository extends JpaRepository<Intake, Long> {

    List<Intake> findByUserMedication(UserMedication userMedication);

    @Query("SELECT i FROM Intake i WHERE i.userMedication.user.id = :userId AND i.takenAt BETWEEN :start AND :end")
    List<Intake> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // N+1 방지: UserMedication을 함께 조회
    @Query("SELECT i FROM Intake i JOIN FETCH i.userMedication WHERE i.userMedication.user.id = :userId AND i.takenAt BETWEEN :start AND :end")
    List<Intake> findByUserIdAndDateRangeWithMedication(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 약물 + 영양제 복용 기록 통합 조회 (오늘의 복약, 복용 달력에서 사용)
     * UserMedication, UserSupplement 모두 Fetch Join
     */
    @Query("SELECT i FROM Intake i " +
           "LEFT JOIN FETCH i.userMedication um " +
           "LEFT JOIN FETCH i.userSupplement us " +
           "WHERE i.takenAt BETWEEN :start AND :end " +
           "AND ((um IS NOT NULL AND um.user.id = :userId) " +
           "  OR (us IS NOT NULL AND us.user.id = :userId))")
    List<Intake> findAllByUserIdAndDateRangeWithDetails(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT i FROM Intake i WHERE i.userMedication.id = :userMedicationId AND i.takenAt BETWEEN :start AND :end")
    List<Intake> findByUserMedicationIdAndDateRange(
            @Param("userMedicationId") Long userMedicationId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(i) FROM Intake i WHERE i.userMedication.user.id = :userId AND i.status = 'TAKEN' AND i.takenAt BETWEEN :start AND :end")
    long countTakenByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 특정 약물의 특정 타이밍 복용 기록 조회 (오늘)
    @Query("SELECT i FROM Intake i WHERE i.userMedication.id = :userMedicationId AND i.timing = :timing AND i.takenAt BETWEEN :start AND :end")
    Optional<Intake> findByUserMedicationIdAndTimingAndDateRange(
            @Param("userMedicationId") Long userMedicationId,
            @Param("timing") MedicationTiming timing,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 의약품의 특정 타이밍에 대한 복용/건너뛰기 기록 존재 여부 확인
     */
    @Query("SELECT COUNT(i) > 0 FROM Intake i WHERE i.userMedication.id = :userMedicationId AND i.timing = :timing AND i.takenAt BETWEEN :start AND :end")
    boolean existsByUserMedicationIdAndTimingAndDateRange(
            @Param("userMedicationId") Long userMedicationId,
            @Param("timing") MedicationTiming timing,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 영양제의 특정 타이밍에 대한 복용/건너뛰기 기록 존재 여부 확인
     */
    @Query("SELECT COUNT(i) > 0 FROM Intake i WHERE i.userSupplement.id = :userSupplementId AND i.timing = :timing AND i.takenAt BETWEEN :start AND :end")
    boolean existsByUserSupplementIdAndTimingAndDateRange(
            @Param("userSupplementId") Long userSupplementId,
            @Param("timing") MedicationTiming timing,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
