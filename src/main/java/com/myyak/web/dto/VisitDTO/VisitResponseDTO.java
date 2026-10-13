package com.myyak.web.dto.VisitDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.util.List;

public class VisitResponseDTO {
    @Getter @AllArgsConstructor
    public static class RecordResult {
        private LocalDate visitDate;
        private long nextVisitInSeconds;
    }

    @Getter @AllArgsConstructor
    public static class Summary {
        private LocalDate today;
        private String timeZone;
        private long todayVisitors;
        private long yesterdayVisitors;
        private List<Day> daily;
    }

    @Getter @AllArgsConstructor
    public static class Day {
        private LocalDate date;
        private long visitors;
    }
}
