package com.myyak.repository;

import com.myyak.domain.QnAReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QnAReplyRepository extends JpaRepository<QnAReply, Long> {

    List<QnAReply> findByQnaIdOrderByCreatedAtAsc(Long qnaId);
}
