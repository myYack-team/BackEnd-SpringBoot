package com.myyak.service.visitService;

import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import java.time.LocalDate;

public interface VisitService {
    void createVisit(Long userId, LocalDate date);
    VisitResponseDTO.Summary getVisitSummary();
}
