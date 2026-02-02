package com.myyak.web.dto.FamilyDTO;

import com.myyak.web.dto.TodayDTO.TodayResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class FamilyResponseDTO {

    /**
     * 가족 연동 현황 (내가 연결한 가족 + 나를 보호자로 등록한 사람들 + 요청 현황)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkStatus {
        private List<LinkedFamily> linkedFamilies;    // 내가 보호자로서 연결한 피보호자 목록
        private List<PendingRequest> receivedRequests; // 내가 받은 연동 요청
        private List<PendingRequest> sentRequests;     // 내가 보낸 연동 요청
        private List<Guardian> guardians;              // 나를 피보호자로 등록한 보호자 목록
        private Integer maxLinkCount;                  // 최대 연동 가능 수
    }

    /**
     * 연동된 가족 정보 (내가 보호자인 경우)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedFamily {
        private Long linkId;
        private Long userId;
        private String name;
        private String profileImage;
        private String phone;          // 마스킹된 전화번호 (예: 010-xxxx-5678)
        private LocalDateTime linkedAt;
        private Boolean isGuardian;    // 내가 보호자인지 여부
    }

    /**
     * 대기 중인 요청 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingRequest {
        private Long requestId;
        private Long userId;
        private String name;
        private String profileImage;
        private String phone;          // 마스킹된 전화번호
        private LocalDateTime requestedAt;
    }

    /**
     * 나를 보호자로 등록한 사람 (내가 피보호자인 경우)
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Guardian {
        private Long linkId;
        private Long userId;
        private String name;
        private String profileImage;
        private LocalDateTime linkedAt;
    }

    /**
     * 연동 요청 결과
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendRequestResult {
        private Long requestId;
        private String targetName;
        private String message;
    }

    /**
     * 가족의 오늘 복약 일정
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyTodaySchedule {
        private Long familyUserId;
        private String familyUserName;
        private String familyProfileImage;
        private TodayResponseDTO.TodayResult todaySchedule;
    }

    /**
     * 가족 알림 설정
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyNotificationSettings {
        private Boolean familyNotificationEnabled;
    }

    /**
     * 월간 요약 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FamilyMonthlySummary {
        private Long familyUserId;
        private String familyUserName;
        private Integer year;
        private Integer month;
        private Integer totalScheduled;     // 해당 월 전체 예정 복약 수
        private Integer totalTaken;         // 복용한 수
        private Integer totalMissed;        // 미복용 수
        private Double adherenceRate;       // 복용률 (%)
        private List<DaySummary> days;      // 일별 복약 요약
    }

    /**
     * 일별 복약 요약
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DaySummary {
        private String date;
        private Integer totalScheduled;
        private Integer totalTaken;
        private String status;  // COMPLETE, PARTIAL, MISSED, PENDING, NONE
    }
}
