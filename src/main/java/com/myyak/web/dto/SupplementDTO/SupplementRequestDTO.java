package com.myyak.web.dto.SupplementDTO;

import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.SupplementTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public class SupplementRequestDTO {

    /**
     * 영양제 마스터 등록 요청 (다른 사용자도 검색/선택 가능)
     */
    @Getter
    @NoArgsConstructor
    public static class CreateSupplementRequest {
        @NotBlank(message = "영양제 이름은 필수입니다")
        private String name;

        private String description;

        @NotNull(message = "영양제 태그는 필수입니다")
        private SupplementTag tag;

        private String imageUrl;
    }

    /**
     * 영양제 마스터 정보 수정 요청
     */
    @Getter
    @NoArgsConstructor
    public static class UpdateSupplementRequest {
        private String name;
        private String description;
        private SupplementTag tag;
        private String imageUrl;
    }

    /**
     * 사용자 영양제 등록 요청 (내 복용 목록에 추가)
     */
    @Getter
    @NoArgsConstructor
    public static class AddUserSupplementRequest {
        @NotNull(message = "영양제 ID는 필수입니다")
        private Long supplementId;

        @NotBlank(message = "복용량은 필수입니다")
        private String dosage;  // "1정", "2캡슐" 등

        @NotNull(message = "복용 횟수는 필수입니다")
        @Positive(message = "복용 횟수는 양수여야 합니다")
        private Integer frequency;

        @NotEmpty(message = "복용 시점은 최소 1개 이상 필요합니다")
        private List<MedicationTiming> timings;

        @NotNull(message = "시작일은 필수입니다")
        private LocalDate startDate;

        private LocalDate endDate;  // null이면 계속 복용

        private String memo;

        private List<String> reminderTimes;  // "HH:mm" 형식, timings와 1:1 대응. null이면 기본 시간 사용
    }

    /**
     * 사용자 영양제 정보 수정 요청
     */
    @Getter
    @NoArgsConstructor
    public static class UpdateUserSupplementRequest {
        private String dosage;
        private Integer frequency;
        private List<MedicationTiming> timings;
        private LocalDate endDate;
        private String memo;
        private List<String> reminderTimes;  // "HH:mm" 형식, timings와 1:1 대응. null이면 기본 시간 사용
    }

    /**
     * 사용자 영양제 일괄 삭제 요청
     */
    @Getter
    @NoArgsConstructor
    public static class BatchDeleteRequest {
        @NotEmpty(message = "삭제할 ID 목록은 필수입니다")
        private List<Long> ids;
    }
}
