package com.myyak.service.prescriptionService;

import com.myyak.web.dto.PrescriptionDTO.PrescriptionRequestDTO;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface PrescriptionService {

    /**
     * 처방전 이미지 업로드
     * @param userId 사용자 ID
     * @param file 이미지 파일
     * @param prescriptionDate 처방 날짜 (null이면 오늘)
     * @return 업로드 결과
     */
    PrescriptionResponseDTO.UploadResult uploadPrescription(Long userId, MultipartFile file, LocalDate prescriptionDate);

    /**
     * 사용자의 처방전 목록 조회
     * @param userId 사용자 ID
     * @return 처방전 목록
     */
    PrescriptionResponseDTO.PrescriptionList getPrescriptionList(Long userId);

    /**
     * 처방전 상세 조회
     * @param userId 사용자 ID
     * @param prescriptionId 처방전 ID
     * @return 처방전 상세 정보 (연결된 약품 포함)
     */
    PrescriptionResponseDTO.PrescriptionDetail getPrescriptionDetail(Long userId, Long prescriptionId);

    /**
     * 처방전 정보 수정
     * @param userId 사용자 ID
     * @param prescriptionId 처방전 ID
     * @param request 수정 요청
     * @return 수정된 처방전 정보
     */
    PrescriptionResponseDTO.PrescriptionInfo updatePrescription(Long userId, Long prescriptionId, PrescriptionRequestDTO.UpdateRequest request);

    /**
     * 처방전 삭제
     * @param userId 사용자 ID
     * @param prescriptionId 처방전 ID
     */
    void deletePrescription(Long userId, Long prescriptionId);

    /**
     * 처방전 일괄 삭제
     * @param userId 사용자 ID
     * @param ids 삭제할 처방전 ID 목록
     * @return 삭제 결과 (요청 수, 실제 삭제 수)
     */
    PrescriptionResponseDTO.BatchDeleteResult deletePrescriptionsBatch(Long userId, java.util.List<Long> ids);

    /**
     * 처방전 + 약물 일괄 등록
     * @param userId 사용자 ID
     * @param file 처방전 이미지 파일
     * @param request 처방전 및 약물 정보
     * @return 등록 결과
     */
    PrescriptionResponseDTO.RegisterResult registerPrescription(Long userId, MultipartFile file, PrescriptionRequestDTO.RegisterRequest request);
}
