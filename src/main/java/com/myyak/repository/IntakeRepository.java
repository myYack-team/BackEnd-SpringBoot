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
}
