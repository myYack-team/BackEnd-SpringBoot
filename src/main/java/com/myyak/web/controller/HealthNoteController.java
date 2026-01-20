package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.apiPayload.code.status.SuccessStatus;
import com.myyak.service.healthNoteService.HealthNoteService;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteRequestDTO;
import com.myyak.web.dto.HealthNoteDTO.HealthNoteResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "HealthNote", description = "건강 메모 API")
@RestController
@RequestMapping("/api/health-notes")
@RequiredArgsConstructor
public class HealthNoteController {

    private final HealthNoteService healthNoteService;

    @Operation(summary = "건강 메모 생성", description = "특정 날짜의 건강 메모를 생성합니다. 컨디션 점수(0~10)와 메모 내용을 저장합니다.")
    @PostMapping
    public ApiResponse<HealthNoteResponseDTO.CreateResult> createNote(
            Authentication authentication,
            @Valid @RequestBody HealthNoteRequestDTO.CreateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.HEALTH_NOTE_CREATED, healthNoteService.createNote(userId, request));
    }

    @Operation(summary = "건강 메모 조회", description = "특정 날짜의 건강 메모를 조회합니다. 해당 날짜에 메모가 없으면 null을 반환합니다.")
    @GetMapping("/{date}")
    public ApiResponse<HealthNoteResponseDTO.Detail> getNote(
            Authentication authentication,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(healthNoteService.getNote(userId, date));
    }

    @Operation(summary = "건강 메모 수정", description = "특정 날짜의 건강 메모를 수정합니다.")
    @PutMapping("/{date}")
    public ApiResponse<HealthNoteResponseDTO.UpdateResult> updateNote(
            Authentication authentication,
            @Parameter(description = "수정할 날짜 (yyyy-MM-dd)")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody HealthNoteRequestDTO.UpdateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.of(SuccessStatus.HEALTH_NOTE_UPDATED, healthNoteService.updateNote(userId, date, request));
    }

    @Operation(summary = "건강 메모 삭제", description = "특정 날짜의 건강 메모를 삭제합니다.")
    @DeleteMapping("/{date}")
    public ApiResponse<Void> deleteNote(
            Authentication authentication,
            @Parameter(description = "삭제할 날짜 (yyyy-MM-dd)")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = (Long) authentication.getPrincipal();
        healthNoteService.deleteNote(userId, date);
        return ApiResponse.of(SuccessStatus.HEALTH_NOTE_DELETED, null);
    }

    @Operation(summary = "건강 메모 목록 조회", description = "지정된 날짜 범위의 건강 메모 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<HealthNoteResponseDTO.NoteList> getNotesByDateRange(
            Authentication authentication,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.onSuccess(healthNoteService.getNotesByDateRange(userId, start, end));
    }
}
