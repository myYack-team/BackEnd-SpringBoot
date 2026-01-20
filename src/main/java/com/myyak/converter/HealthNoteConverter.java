package com.myyak.converter;

import com.myyak.domain.HealthNote;
import com.myyak.domain.User;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteRequestDTO;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteResponseDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * 건강 메모 Converter
 */
public class HealthNoteConverter {

    /**
     * 요청 DTO → Entity 변환 (생성 시)
     */
    public static HealthNote toEntity(HealthNoteRequestDTO.CreateRequest request, User user) {
        return HealthNote.builder()
                .user(user)
                .noteDate(request.getNoteDate())
                .conditionScore(request.getConditionScore() != null ? request.getConditionScore() : 10)
                .content(request.getContent())
                .build();
    }

    /**
     * Entity → 상세 DTO 변환
     */
    public static HealthNoteResponseDTO.Detail toDetail(HealthNote entity) {
        return HealthNoteResponseDTO.Detail.builder()
                .id(entity.getId())
                .noteDate(entity.getNoteDate())
                .conditionScore(entity.getConditionScore())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Entity → 목록 아이템 DTO 변환
     */
    public static HealthNoteResponseDTO.ListItem toListItem(HealthNote entity) {
        return HealthNoteResponseDTO.ListItem.builder()
                .id(entity.getId())
                .noteDate(entity.getNoteDate())
                .conditionScore(entity.getConditionScore())
                .content(entity.getContent())
                .build();
    }

    /**
     * Entity 목록 → 목록 DTO 변환
     */
    public static HealthNoteResponseDTO.NoteList toNoteList(
            List<HealthNote> entities,
            LocalDate startDate,
            LocalDate endDate) {
        List<HealthNoteResponseDTO.ListItem> items = entities.stream()
                .map(HealthNoteConverter::toListItem)
                .toList();

        return HealthNoteResponseDTO.NoteList.builder()
                .notes(items)
                .totalCount(items.size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    /**
     * Entity → 생성 결과 DTO 변환
     */
    public static HealthNoteResponseDTO.CreateResult toCreateResult(HealthNote entity) {
        return HealthNoteResponseDTO.CreateResult.builder()
                .id(entity.getId())
                .noteDate(entity.getNoteDate())
                .conditionScore(entity.getConditionScore())
                .content(entity.getContent())
                .build();
    }

    /**
     * Entity → 수정 결과 DTO 변환
     */
    public static HealthNoteResponseDTO.UpdateResult toUpdateResult(HealthNote entity) {
        return HealthNoteResponseDTO.UpdateResult.builder()
                .id(entity.getId())
                .noteDate(entity.getNoteDate())
                .conditionScore(entity.getConditionScore())
                .content(entity.getContent())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
