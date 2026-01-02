package com.myyak.web.dto.AdminDTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AdminRequestDTO {

    /**
     * 영양제 목록 조회 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SupplementListRequest {
        private int page = 0;
        private int size = 10;
        private Integer days = 7;  // 최근 N일 (null이면 전체)
        private String search;      // 검색어
    }
}
