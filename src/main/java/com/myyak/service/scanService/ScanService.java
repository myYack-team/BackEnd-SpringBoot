package com.myyak.service.scanService;

import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ScanService {

    /**
     * 처방전 스캔 (기존 LLM 방식)
     * - Vision LLM으로 약물 인식
     * - DB 키워드 검색으로 약물 매칭
     */
    ScanResponseDTO.ScanResult scanPrescription(MultipartFile image);

    /**
     * 처방전 스캔 (통합 방식: LLM + Embedding)
     * - Vision LLM으로 약물 인식
     * - DB 키워드 검색으로 약물 매칭
     * - 매칭 실패 시 Embedding 기반 유사 약물 추천
     */
    ScanResponseDTO.ScanResult scanPrescriptionWithEmbedding(MultipartFile image);
}
