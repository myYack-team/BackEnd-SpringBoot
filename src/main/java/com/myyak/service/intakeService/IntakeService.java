package com.myyak.service.intakeService;

import com.myyak.web.dto.IntakeDTO.IntakeRequestDTO;
import com.myyak.web.dto.IntakeDTO.IntakeResponseDTO;

import java.time.LocalDate;

public interface IntakeService {

    IntakeResponseDTO.CreateResult recordIntake(Long userId, IntakeRequestDTO.CreateRequest request);

    IntakeResponseDTO.DailyIntakeResult getIntakes(Long userId, LocalDate date);

    IntakeResponseDTO.MonthlySummaryResult getMonthlySummary(Long userId, int year, int month);
}
