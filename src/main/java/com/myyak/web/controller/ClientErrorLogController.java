package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.errorLogService.ClientErrorLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "ErrorLog", description = "클라이언트 에러 로그 API")
@Slf4j
@RestController
@RequestMapping("/api/error-log")
@RequiredArgsConstructor
public class ClientErrorLogController {

    private final ClientErrorLogService clientErrorLogService;

    @Operation(summary = "에러 로그 전송", description = "클라이언트에서 발생한 에러를 서버에 기록합니다.")
    @PostMapping
    public ApiResponse<Void> reportError(
            @Valid @RequestBody ErrorLogRequest request,
            Authentication authentication) {

        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            userId = (Long) authentication.getPrincipal();
        }

        clientErrorLogService.saveErrorLog(
                request.getLevel(),
                request.getMessage(),
                request.getStackTrace(),
                request.getScreen(),
                request.getAppVersion(),
                request.getPlatform(),
                request.getOsVersion(),
                request.getDeviceModel(),
                userId,
                request.getAdditionalInfo()
        );

        return ApiResponse.onSuccess(null);
    }

    @Operation(summary = "에러 로그 파일 목록 조회 (관리자)", description = "최근 N일간의 에러 로그 파일 목록을 조회합니다.")
    @GetMapping("/files")
    public ApiResponse<List<String>> getLogFiles(
            @Parameter(description = "조회할 일수 (기본 30일)")
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.onSuccess(clientErrorLogService.getLogFileList(days));
    }

    @Operation(summary = "특정 날짜 에러 로그 조회 (관리자)", description = "특정 날짜의 에러 로그 내용을 조회합니다.")
    @GetMapping
    public ApiResponse<String> getLogContent(
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.onSuccess(clientErrorLogService.getLogFileContent(date));
    }

    @Getter
    @NoArgsConstructor
    public static class ErrorLogRequest {
        @NotBlank(message = "에러 레벨은 필수입니다")
        @Size(max = 20)
        private String level;

        @NotBlank(message = "에러 메시지는 필수입니다")
        @Size(max = 500)
        private String message;

        private String stackTrace;

        @Size(max = 100)
        private String screen;

        @Size(max = 50)
        private String appVersion;

        @Size(max = 20)
        private String platform;

        @Size(max = 50)
        private String osVersion;

        @Size(max = 100)
        private String deviceModel;

        private String additionalInfo;
    }
}
