package com.myyak.repository;

import com.myyak.domain.FamilyLinkRequest;
import com.myyak.domain.enums.FamilyLinkRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FamilyLinkRequestRepository extends JpaRepository<FamilyLinkRequest, Long> {

    /**
     * 내가 보낸 요청 목록 (상태별)
     */
    @Query("SELECT flr FROM FamilyLinkRequest flr JOIN FETCH flr.target WHERE flr.requester.id = :requesterId AND flr.status = :status")
    List<FamilyLinkRequest> findByRequesterIdAndStatus(@Param("requesterId") Long requesterId, @Param("status") FamilyLinkRequestStatus status);

    /**
     * 내가 받은 요청 목록 (상태별)
     */
    @Query("SELECT flr FROM FamilyLinkRequest flr JOIN FETCH flr.requester WHERE flr.target.id = :targetId AND flr.status = :status")
    List<FamilyLinkRequest> findByTargetIdAndStatus(@Param("targetId") Long targetId, @Param("status") FamilyLinkRequestStatus status);

    /**
     * 특정 요청자-대상 간 대기 중인 요청 존재 여부
     */
    boolean existsByRequesterIdAndTargetIdAndStatus(Long requesterId, Long targetId, FamilyLinkRequestStatus status);

    /**
     * 요청 ID와 대상 ID로 조회 (수락/거절용)
     */
    Optional<FamilyLinkRequest> findByIdAndTargetId(Long id, Long targetId);

    /**
     * 요청 ID와 요청자 ID로 조회 (취소용)
     */
    Optional<FamilyLinkRequest> findByIdAndRequesterId(Long id, Long requesterId);

    /**
     * 회원 탈퇴 시 요청자/대상자로 연결된 모든 가족 연결 요청 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM FamilyLinkRequest fr WHERE fr.requester.id = :userId OR fr.target.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
