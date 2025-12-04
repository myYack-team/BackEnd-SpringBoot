package com.myyak.apiPayload.exception;

import com.myyak.apiPayload.code.BaseErrorCode;
import com.myyak.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException {

    private BaseErrorCode code;

    public GeneralException(BaseErrorCode code, Throwable cause) {
        super(messageOrNull(code), cause);
        this.code = code;
    }

    public ErrorReasonDTO getErrorReason() {
        return this.code.getReason();
    }

    public ErrorReasonDTO getErrorReasonHttpStatus() {
        return this.code.getReasonHttpStatus();
    }

    private static String messageOrNull(BaseErrorCode code) {
        try {
            ErrorReasonDTO reason = code.getReason();
            return reason != null ? reason.getMessage() : null;
        } catch (Exception ignore) {
            return null;
        }
    }
}
