package com.myyak.service.scanService;

import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ScanService {

    /**
     * 처방전 스캔
     * - Vision LLM으로 약물 인식
     * - DB 키워드 검색으로 약물 매칭
     * - 매칭 실패 시 자모 기반 편집거리 검색으로 OCR 오타 보정
     */
    ScanResponseDTO.ScanResult scanPrescription(MultipartFile image);
}
