package com.myyak.service.healthNoteService;

import com.myyak.web.dto.HealthNoteDTO.HealthNoteRequestDTO;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteResponseDTO;

import java.time.LocalDate;

/**
 * 건강 메모 서비스 인터페이스
 */
public interface HealthNoteService {

    /**
     * 건강 메모 생성
     * @param userId 사용자 ID
     * @param request 생성 요청 DTO
     * @return 생성 결과
     */
    HealthNoteResponseDTO.CreateResult createNote(Long userId, HealthNoteRequestDTO.CreateRequest request);

    /**
     * 특정 날짜의 건강 메모 조회
     * @param userId 사용자 ID
     * @param date 조회할 날짜
     * @return 건강 메모 상세 정보 (없으면 null)
     */
    HealthNoteResponseDTO.Detail getNote(Long userId, LocalDate date);

    /**
     * 건강 메모 수정
     * @param userId 사용자 ID
     * @param date 수정할 날짜
     * @param request 수정 요청 DTO
     * @return 수정 결과
     */
    HealthNoteResponseDTO.UpdateResult updateNote(Long userId, LocalDate date, HealthNoteRequestDTO.UpdateRequest request);

    /**
     * 건강 메모 삭제
     * @param userId 사용자 ID
     * @param date 삭제할 날짜
     */
    void deleteNote(Long userId, LocalDate date);

    /**
     * 날짜 범위로 건강 메모 목록 조회
     * @param userId 사용자 ID
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 건강 메모 목록
     */
    HealthNoteResponseDTO.NoteList getNotesByDateRange(Long userId, LocalDate startDate, LocalDate endDate);
}
