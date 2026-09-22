package com.myyak.service.authService.store;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;

public class TemporaryAuthStoreException extends GeneralException {

    public TemporaryAuthStoreException(Throwable cause) {
        super(ErrorStatus.AUTH_TEMPORARY_STORE_UNAVAILABLE, cause);
    }
}
