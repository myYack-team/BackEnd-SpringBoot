package com.myyak.repository;

import com.myyak.domain.QnAReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QnAReplyRepository extends JpaRepository<QnAReply, Long> {

    /**
     * 회원 탈퇴 시 사용자의 문의에 달린 답변 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM QnAReply r WHERE r.qna.id IN (SELECT q.id FROM QnA q WHERE q.user.id = :userId)")
    void deleteAllByUserId(@Param("userId") Long userId);
}
