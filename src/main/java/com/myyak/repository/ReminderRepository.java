package com.myyak.repository;

import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByUserMedication(UserMedication userMedication);

    void deleteByUserMedication(UserMedication userMedication);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE um.user.id = :userId AND um.isActive = true")
    List<Reminder> findByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE um.user.id = :userId AND r.enabled = true AND um.isActive = true")
    List<Reminder> findEnabledByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Reminder r JOIN r.userMedication um WHERE r.time = :time AND r.enabled = true AND um.isActive = true")
    List<Reminder> findByTimeAndEnabledTrue(@Param("time") LocalTime time);

    Optional<Reminder> findByUserMedicationAndTiming(UserMedication userMedication, MedicationTiming timing);
}
