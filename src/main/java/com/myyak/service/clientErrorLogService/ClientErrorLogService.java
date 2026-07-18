package com.myyak.service.clientErrorLogService;

import java.time.LocalDate;
import java.util.List;

public interface ClientErrorLogService {

    /**
     * 클라이언트 에러 로그 저장
     */
    void saveErrorLog(String level, String message, String stackTrace, String screen,
                      String appVersion, String platform, String osVersion,
                      String deviceModel, Long userId, String additionalInfo);

    /**
     * 특정 날짜의 에러 로그 조회
     */
    List<String> getErrorLogs(LocalDate date);

    /**
     * 최근 N일간의 에러 로그 파일 목록 조회
     */
    List<String> getLogFileList(int days);

    /**
     * 특정 날짜의 에러 로그 파일 내용 조회
     */
    String getLogFileContent(LocalDate date);
}
