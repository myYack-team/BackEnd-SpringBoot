package com.myyak.repository;

import com.myyak.domain.UserVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface UserVisitRepository extends JpaRepository<UserVisit, Long> {
    boolean existsByUserIdAndVisitDate(Long userId, LocalDate visitDate);

    interface DailyCount {
        LocalDate getVisitDate();
        long getVisitors();
    }

    @Query("SELECT v.visitDate AS visitDate, COUNT(v) AS visitors FROM UserVisit v " +
            "WHERE v.visitDate BETWEEN :from AND :to GROUP BY v.visitDate ORDER BY v.visitDate")
    List<DailyCount> countDailyVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying
    @Query("DELETE FROM UserVisit v WHERE v.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
