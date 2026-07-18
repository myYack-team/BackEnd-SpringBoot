package com.myyak.repository;

import com.myyak.domain.Prescription;
import com.myyak.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /**
     * 사용자의 처방전 목록 조회 (최신순)
     */
    List<Prescription> findByUserOrderByPrescriptionDateDesc(User user);

    /**
     * 사용자의 처방전 목록 조회 (페이징)
     */
    Page<Prescription> findByUserOrderByPrescriptionDateDesc(User user, Pageable pageable);

    /**
     * 사용자 ID로 처방전 목록 조회
     */
    @Query("SELECT p FROM Prescription p WHERE p.user.id = :userId ORDER BY p.prescriptionDate DESC")
    List<Prescription> findByUserId(@Param("userId") Long userId);
}
