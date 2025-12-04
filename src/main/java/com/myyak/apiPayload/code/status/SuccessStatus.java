package com.myyak.apiPayload.code.status;

import com.myyak.apiPayload.code.BaseCode;
import com.myyak.apiPayload.code.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

    // 공통 성공 응답
    _OK(HttpStatus.OK, "COMMON200", "성공입니다."),
    _CREATED(HttpStatus.CREATED, "COMMON201", "생성되었습니다."),

    // 인증 관련
    LOGIN_SUCCESS(HttpStatus.OK, "AUTH200", "로그인에 성공했습니다."),
    TOKEN_REFRESHED(HttpStatus.OK, "AUTH201", "토큰이 갱신되었습니다."),
    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH202", "로그아웃되었습니다."),

    // 약 관련
    MEDICATION_CREATED(HttpStatus.CREATED, "MED201", "약이 등록되었습니다."),
    MEDICATION_UPDATED(HttpStatus.OK, "MED200", "약 정보가 수정되었습니다."),
    MEDICATION_DELETED(HttpStatus.OK, "MED202", "약이 삭제되었습니다."),

    // 스캔 관련
    SCAN_SUCCESS(HttpStatus.OK, "SCAN200", "처방전 인식에 성공했습니다."),
    SCAN_RETRY_RECOMMENDED(HttpStatus.OK, "SCAN201", "인식 정확도가 낮습니다. 재촬영을 권장합니다."),

    // 복약 기록 관련
    INTAKE_RECORDED(HttpStatus.CREATED, "INTAKE201", "복약이 기록되었습니다."),

    // 알림 관련
    REMINDER_UPDATED(HttpStatus.OK, "REMINDER200", "알림이 수정되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getReason() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .build();
    }

    @Override
    public ReasonDTO getReasonHttpStatus() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .httpStatus(httpStatus)
                .build();
    }
}
