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

    // 영양제 관련
    SUPPLEMENT_CREATED(HttpStatus.CREATED, "SUPP201", "영양제가 등록되었습니다."),
    SUPPLEMENT_UPDATED(HttpStatus.OK, "SUPP200", "영양제 정보가 수정되었습니다."),
    SUPPLEMENT_DELETED(HttpStatus.OK, "SUPP202", "영양제가 삭제되었습니다."),
    USER_SUPPLEMENT_CREATED(HttpStatus.CREATED, "USUPP201", "영양제가 내 목록에 추가되었습니다."),
    USER_SUPPLEMENT_UPDATED(HttpStatus.OK, "USUPP200", "영양제 복용 정보가 수정되었습니다."),
    USER_SUPPLEMENT_DELETED(HttpStatus.OK, "USUPP202", "영양제가 내 목록에서 삭제되었습니다."),
    USER_SUPPLEMENTS_BATCH_DELETED(HttpStatus.OK, "USUPP203", "내 영양제 일괄 삭제 성공"),

    // 스캔 관련
    SCAN_SUCCESS(HttpStatus.OK, "SCAN200", "처방전 인식에 성공했습니다."),
    SCAN_RETRY_RECOMMENDED(HttpStatus.OK, "SCAN201", "인식 정확도가 낮습니다. 재촬영을 권장합니다."),

    // 복약 기록 관련
    INTAKE_RECORDED(HttpStatus.CREATED, "INTAKE201", "복약이 기록되었습니다."),

    // 알림 관련
    REMINDER_UPDATED(HttpStatus.OK, "REMINDER200", "알림이 수정되었습니다."),
    REMINDER_SNOOZED(HttpStatus.OK, "REMINDER201", "다시 알림이 설정되었습니다."),
    REMINDER_SNOOZE_CLEARED(HttpStatus.OK, "REMINDER202", "다시 알림이 해제되었습니다."),

    // AI 분석 관련
    ANALYSIS_COMPLETED(HttpStatus.OK, "ANALYSIS200", "AI 약물 분석이 완료되었습니다."),
    ANALYSIS_REPORT_DELETED(HttpStatus.OK, "ANALYSIS201", "분석 레포트가 삭제되었습니다."),

    // 건강 메모 관련
    HEALTH_NOTE_CREATED(HttpStatus.CREATED, "HNOTE201", "건강 메모가 저장되었습니다."),
    HEALTH_NOTE_UPDATED(HttpStatus.OK, "HNOTE200", "건강 메모가 수정되었습니다."),
    HEALTH_NOTE_DELETED(HttpStatus.OK, "HNOTE202", "건강 메모가 삭제되었습니다.");

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
