package com.myyak.service.healthNoteService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.HealthNoteConverter;
import com.myyak.domain.HealthNote;
import com.myyak.domain.User;
import com.myyak.repository.HealthNoteRepository;
import com.myyak.repository.UserRepository;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteRequestDTO;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 건강 메모 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthNoteServiceImpl implements HealthNoteService {

    private final HealthNoteRepository healthNoteRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public HealthNoteResponseDTO.CreateResult createNote(Long userId, HealthNoteRequestDTO.CreateRequest request) {
        User user = findUserById(userId);

        // 해당 날짜에 이미 메모가 있는지 확인
        if (healthNoteRepository.existsByUserAndNoteDate(user, request.getNoteDate())) {
            throw new GeneralException(ErrorStatus.HEALTH_NOTE_ALREADY_EXISTS);
        }

        HealthNote healthNote = HealthNoteConverter.toEntity(request, user);
        healthNoteRepository.save(healthNote);

        log.info("[HealthNote] 건강 메모 생성 - userId: {}, date: {}, score: {}",
                userId, request.getNoteDate(), healthNote.getConditionScore());

        return HealthNoteConverter.toCreateResult(healthNote);
    }

    @Override
    public HealthNoteResponseDTO.Detail getNote(Long userId, LocalDate date) {
        User user = findUserById(userId);

        return healthNoteRepository.findByUserAndNoteDate(user, date)
                .map(HealthNoteConverter::toDetail)
                .orElse(null);
    }

    @Override
    @Transactional
    public HealthNoteResponseDTO.UpdateResult updateNote(Long userId, LocalDate date, HealthNoteRequestDTO.UpdateRequest request) {
        User user = findUserById(userId);

        HealthNote healthNote = healthNoteRepository.findByUserAndNoteDate(user, date)
                .orElseThrow(() -> new GeneralException(ErrorStatus.HEALTH_NOTE_NOT_FOUND));

        healthNote.update(request.getConditionScore(), request.getContent());

        log.info("[HealthNote] 건강 메모 수정 - userId: {}, date: {}, score: {}",
                userId, date, healthNote.getConditionScore());

        return HealthNoteConverter.toUpdateResult(healthNote);
    }

    @Override
    @Transactional
    public void deleteNote(Long userId, LocalDate date) {
        User user = findUserById(userId);

        if (!healthNoteRepository.existsByUserAndNoteDate(user, date)) {
            throw new GeneralException(ErrorStatus.HEALTH_NOTE_NOT_FOUND);
        }

        healthNoteRepository.deleteByUserAndNoteDate(user, date);

        log.info("[HealthNote] 건강 메모 삭제 - userId: {}, date: {}", userId, date);
    }

    @Override
    public HealthNoteResponseDTO.NoteList getNotesByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        User user = findUserById(userId);

        List<HealthNote> notes = healthNoteRepository.findByUserAndNoteDateBetween(user, startDate, endDate);

        log.debug("[HealthNote] 건강 메모 목록 조회 - userId: {}, range: {} ~ {}, count: {}",
                userId, startDate, endDate, notes.size());

        return HealthNoteConverter.toNoteList(notes, startDate, endDate);
    }

    // ===== Private Methods =====

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
    }
}
