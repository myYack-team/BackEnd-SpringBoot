package com.myyak.web.dto.QnADTO;

import com.myyak.domain.enums.QnAStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class QnARequestDTO {

    @Getter
    @NoArgsConstructor
    public static class CreateQuestion {
        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 100, message = "제목은 100자 이내로 작성해주세요")
        private String title;

        @NotBlank(message = "내용은 필수입니다")
        private String content;
    }

    @Getter
    @NoArgsConstructor
    public static class CreateReply {
        @NotBlank(message = "답변 내용은 필수입니다")
        private String content;
    }

    @Getter
    @NoArgsConstructor
    public static class AdminReply {
        @NotBlank(message = "답변 내용은 필수입니다")
        private String content;

        private String adminName;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateStatus {
        @NotNull(message = "상태는 필수입니다")
        private QnAStatus status;
    }
}
