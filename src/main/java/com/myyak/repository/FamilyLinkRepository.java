package com.myyak.repository;

import com.myyak.domain.FamilyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
     * 회원 탈퇴 시 보호자/피보호자로 연결된 모든 가족 링크 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM FamilyLink f WHERE f.guardian.id = :userId OR f.protectedUser.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
