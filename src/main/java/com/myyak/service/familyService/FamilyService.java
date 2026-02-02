package com.myyak.service.familyService;

import com.myyak.web.dto.FamilyDTO.FamilyRequestDTO;
import com.myyak.web.dto.FamilyDTO.FamilyResponseDTO;

import java.time.LocalDate;

public interface FamilyService {

    /**
     * 가족 연동 현황 조회
     */
    FamilyResponseDTO.LinkStatus getLinkStatus(Long userId);

    /**
     * 가족 연동 요청 전송
     */
    FamilyResponseDTO.SendRequestResult sendLinkRequest(Long requesterId, FamilyRequestDTO.SendRequest request);

    /**
     * 가족 연동 요청 취소
     */
    void cancelLinkRequest(Long requesterId, Long requestId);

    /**
     * 가족 연동 요청 수락
     */
    void acceptLinkRequest(Long targetUserId, Long requestId);

    /**
     * 가족 연동 요청 거절
     */
    void rejectLinkRequest(Long targetUserId, Long requestId);

    /**
     * 가족 연동 해제
     */
    void unlinkFamily(Long userId, Long linkId);

    /**
     * 피보호자의 오늘 복약 일정 조회 (보호자용)
     */
    FamilyResponseDTO.FamilyTodaySchedule getFamilyTodaySchedule(Long guardianId, Long protectedUserId);

    /**
     * 피보호자의 특정 날짜 복약 일정 조회 (보호자용)
     */
    FamilyResponseDTO.FamilyTodaySchedule getFamilyScheduleForDate(Long guardianId, Long protectedUserId, LocalDate date);

    /**
     * 피보호자의 월간 복약 요약 조회 (보호자용)
     */
    FamilyResponseDTO.FamilyMonthlySummary getFamilyMonthlySummary(Long guardianId, Long protectedUserId, Integer year, Integer month);

    /**
     * 가족 알림 설정 수정
     */
    FamilyResponseDTO.FamilyNotificationSettings updateFamilyNotificationSettings(Long userId, FamilyRequestDTO.UpdateFamilyNotificationSettings request);

    /**
     * 가족 알림 설정 조회
     */
    FamilyResponseDTO.FamilyNotificationSettings getFamilyNotificationSettings(Long userId);
}
