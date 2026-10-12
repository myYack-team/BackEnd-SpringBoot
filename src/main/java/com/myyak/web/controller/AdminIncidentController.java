package com.myyak.web.controller;

import com.myyak.apiPayload.ApiResponse;
import com.myyak.service.incidentService.IncidentService;
import com.myyak.web.dto.AdminDTO.IncidentResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/incidents")
@Tag(name = "AdminIncident", description = "장애 분석·메일 승인·배포 처리 현황 조회")
public class AdminIncidentController {
    private final IncidentService service;
    @GetMapping
    @Operation(summary = "최근 장애 대응 이력 20건 조회")
    public ApiResponse<IncidentResponseDTO.Page> getIncidents(
            @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) String cursor,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.onSuccess(service.getIncidents(cursor));
    }
    @GetMapping("/{id}")
    @Operation(summary = "장애 대응 상세와 처리 이력 조회")
    public ApiResponse<IncidentResponseDTO.Incident> getIncident(
            @Parameter(description = "사건 UUID") @PathVariable String id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.onSuccess(service.getIncident(id));
    }
}
