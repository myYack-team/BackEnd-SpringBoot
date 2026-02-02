package com.myyak.repository;

import com.myyak.domain.FamilyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyLinkRepository extends JpaRepository<FamilyLink, Long> {

    /**
     * 보호자가 연결한 피보호자 목록 조회
     */
    @Query("SELECT fl FROM FamilyLink fl JOIN FETCH fl.protectedUser WHERE fl.guardian.id = :guardianId")
    List<FamilyLink> findByGuardianId(@Param("guardianId") Long guardianId);

    /**
     * 피보호자를 관리하는 보호자 목록 조회
     */
    @Query("SELECT fl FROM FamilyLink fl JOIN FETCH fl.guardian WHERE fl.protectedUser.id = :protectedUserId")
    List<FamilyLink> findByProtectedUserId(@Param("protectedUserId") Long protectedUserId);

    /**
     * 특정 보호자-피보호자 연결 존재 여부 확인
     */
    boolean existsByGuardianIdAndProtectedUserId(Long guardianId, Long protectedUserId);

    /**
     * 보호자가 연결한 피보호자 수
     */
    long countByGuardianId(Long guardianId);

    /**
     * 특정 보호자-피보호자 연결 조회
     */
    Optional<FamilyLink> findByGuardianIdAndProtectedUserId(Long guardianId, Long protectedUserId);

    /**
     * 보호자 또는 피보호자 ID로 연결 조회
     */
    @Query("SELECT fl FROM FamilyLink fl WHERE fl.guardian.id = :userId OR fl.protectedUser.id = :userId")
    List<FamilyLink> findByGuardianIdOrProtectedUserId(@Param("userId") Long userId);
}
