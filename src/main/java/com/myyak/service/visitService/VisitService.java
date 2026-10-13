package com.myyak.service.visitService;

import com.myyak.web.dto.VisitDTO.VisitResponseDTO;

public interface VisitService {
    VisitResponseDTO.RecordResult createVisit(Long userId);
    VisitResponseDTO.Summary getVisitSummary();
}
