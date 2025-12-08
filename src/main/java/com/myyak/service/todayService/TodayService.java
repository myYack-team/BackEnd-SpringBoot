package com.myyak.service.todayService;

import com.myyak.web.dto.TodayDTO.TodayResponseDTO;

public interface TodayService {

    TodayResponseDTO.TodayResult getTodaySchedule(Long userId);
}
