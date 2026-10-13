package com.myyak.converter;

import com.myyak.repository.UserVisitRepository;
import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VisitConverter {
    private VisitConverter() {}

    public static VisitResponseDTO.RecordResult toRecordResult(LocalDate date, long nextVisitInSeconds) {
        return new VisitResponseDTO.RecordResult(date, nextVisitInSeconds);
    }

    public static VisitResponseDTO.Summary toSummary(LocalDate today, List<UserVisitRepository.DailyCount> counts) {
        Map<LocalDate, Long> byDate = counts.stream().collect(Collectors.toMap(
                UserVisitRepository.DailyCount::getVisitDate, UserVisitRepository.DailyCount::getVisitors));
        List<VisitResponseDTO.Day> daily = IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> today.minusDays(6 - offset))
                .map(date -> new VisitResponseDTO.Day(date, byDate.getOrDefault(date, 0L))).toList();
        return new VisitResponseDTO.Summary(today, "Asia/Seoul", byDate.getOrDefault(today, 0L),
                byDate.getOrDefault(today.minusDays(1), 0L), daily);
    }
}
