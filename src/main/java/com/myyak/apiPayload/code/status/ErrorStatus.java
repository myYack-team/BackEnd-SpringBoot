package com.myyak.apiPayload.code.status;

import com.myyak.apiPayload.code.BaseErrorCode;
import com.myyak.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // 공통 에러
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "접근 권한이 없습니다."),
    _NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),

    // 인증 관련 에러
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH402", "만료된 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH403", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_KAKAO_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH404", "카카오 로그인에 실패했습니다."),
    AUTH_INVALID_CODE(HttpStatus.BAD_REQUEST, "AUTH406", "유효하지 않은 인증 코드입니다."),

    // 사용자 관련 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER409", "이미 존재하는 사용자입니다."),

    // 약 관련 에러
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MED404", "약을 찾을 수 없습니다."),
    MEDICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MED403", "해당 약에 대한 접근 권한이 없습니다."),

    // 영양제 관련 에러
    SUPPLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SUPP404", "영양제를 찾을 수 없습니다."),
    SUPPLEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SUPP403", "해당 영양제에 대한 접근 권한이 없습니다."),
    USER_SUPPLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "USUPP404", "사용자 영양제를 찾을 수 없습니다."),
    USER_SUPPLEMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "USUPP409", "이미 등록된 영양제입니다."),

    // 스캔 관련 에러
    SCAN_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "SCAN400", "이미지가 필요합니다."),
    SCAN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SCAN500", "처방전 인식에 실패했습니다."),
    SCAN_CONFIDENCE_TOO_LOW(HttpStatus.BAD_REQUEST, "SCAN401", "인식 정확도가 너무 낮습니다. 다시 촬영해주세요."),

    // 알림 관련 에러
    REMINDER_NOT_FOUND(HttpStatus.NOT_FOUND, "REMINDER404", "알림을 찾을 수 없습니다."),

    // 복약 기록 관련 에러
    INTAKE_NOT_FOUND(HttpStatus.NOT_FOUND, "INTAKE404", "복약 기록을 찾을 수 없습니다."),

    // 외부 API 에러
    EXTERNAL_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXT503", "외부 서비스 연동에 실패했습니다."),
    VISION_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXT504", "Vision AI 서비스 연동에 실패했습니다."),

    // AI 분석 관련 에러
    ANALYSIS_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ANALYSIS429", "이번 달 분석 횟수를 초과했습니다."),
    ANALYSIS_WEEKLY_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ANALYSIS4291", "이번 주 분석 횟수를 초과했습니다. 다음 주 월요일에 다시 이용해주세요."),
    ANALYSIS_MONTHLY_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "ANALYSIS008", "이번 달 분석 횟수를 모두 사용했습니다"),
    ANALYSIS_NO_MEDICATIONS(HttpStatus.BAD_REQUEST, "ANALYSIS400", "분석할 복용 중인 약물이 없습니다."),
    ANALYSIS_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS404", "분석 레포트를 찾을 수 없습니다."),
    ANALYSIS_LLM_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS503", "AI 분석 서비스 연동에 실패했습니다."),

    // Q&A 관련 에러
    QNA_NOT_FOUND(HttpStatus.NOT_FOUND, "QNA404", "문의를 찾을 수 없습니다."),
    QNA_ACCESS_DENIED(HttpStatus.FORBIDDEN, "QNA403", "해당 문의에 대한 접근 권한이 없습니다."),

    // 건강 메모 관련 에러
    HEALTH_NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "HNOTE404", "건강 메모를 찾을 수 없습니다."),
    HEALTH_NOTE_ALREADY_EXISTS(HttpStatus.CONFLICT, "HNOTE409", "해당 날짜에 이미 건강 메모가 존재합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
