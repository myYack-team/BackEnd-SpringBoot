package com.myyak.converter;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.ScanDTO.ScanResponseDTO;

import java.util.List;

/**
 * 처방전 스캔 결과 Converter
 * Vision/OCR-LLM 스캔 서비스의 응답 DTO 조립을 담당합니다.
 */
public class ScanConverter {

    /**
     * 스캔 결과 DTO 조립
     */
    public static ScanResponseDTO.ScanResult toScanResult(
            Boolean success, String confidence,
            List<ScanResponseDTO.ScannedMedication> medications, String notes,
            String patientName, String hospitalName, String diagnosis, Integer durationDays) {
        return ScanResponseDTO.ScanResult.builder()
                .success(success)
                .confidence(confidence)
                .medications(medications)
                .notes(notes)
                .patientName(patientName)
                .hospitalName(hospitalName)
                .diagnosis(diagnosis)
                .durationDays(durationDays)
                .build();
    }

    /**
     * 실패(저신뢰) 결과 DTO 조립
     */
    public static ScanResponseDTO.ScanResult toFailureResult(String notes) {
        return ScanResponseDTO.ScanResult.builder()
                .success(false)
                .confidence("low")
                .medications(List.of())
                .notes(notes)
                .build();
    }

    /**
     * 민감 정보 마스킹 결과 DTO 조립 (진단명은 항상 마스킹)
     */
    public static ScanResponseDTO.ScanResult toMaskedResult(
            ScanResponseDTO.ScanResult result, String maskedPatientName) {
        return ScanResponseDTO.ScanResult.builder()
                .success(result.getSuccess())
                .confidence(result.getConfidence())
                .patientName(maskedPatientName)
                .hospitalName(result.getHospitalName())
                .diagnosis("***")  // 진단명은 민감 정보이므로 마스킹
                .durationDays(result.getDurationDays())
                .medications(result.getMedications())
                .notes(result.getNotes())
                .build();
    }

    /**
     * 스캔 약물 DTO 조립 (DrugInfo 매칭 시 상세 정보 포함)
     * 약품명 정제/성분명 추출 규칙은 서비스별로 다르므로 호출부에서 계산하여 전달합니다.
     *
     * @param matchedDrug           매칭된 DrugInfo (없으면 null)
     * @param pureDrugName          정제된 약품명 (matchedDrug가 있을 때만 사용)
     * @param ingredient            성분명 (matchedDrug가 있을 때만 사용)
     * @param matchedByEditDistance 편집거리 기반 매칭 여부
     */
    public static ScanResponseDTO.ScannedMedication toScannedMedication(
            String name, Integer dosage, Integer frequency, List<MedicationTiming> timings,
            Integer durationDays, Integer totalCount,
            DrugInfo matchedDrug, String pureDrugName, String ingredient, boolean matchedByEditDistance) {
        ScanResponseDTO.ScannedMedication.ScannedMedicationBuilder builder =
                ScanResponseDTO.ScannedMedication.builder()
                        .name(name)
                        .dosage(dosage)
                        .frequency(frequency)
                        .timings(timings)
                        .durationDays(durationDays)
                        .totalCount(totalCount);

        // DrugInfo가 매칭되면 추가 정보 설정
        if (matchedDrug != null) {
            builder.name(pureDrugName)
                    .drugItemSeq(matchedDrug.getItemSeq())
                    .ingredient(ingredient)
                    .efficacy(matchedDrug.getEfficacy())
                    .imageUrl(matchedDrug.getImageUrl())
                    .entpName(matchedDrug.getEntpName());
            if (matchedByEditDistance) {
                builder.matchedByEditDistance(true);
            }
        }

        return builder.build();
    }

    /**
     * API 키 미설정 시 목업 스캔 결과 조립
     */
    public static ScanResponseDTO.ScanResult toMockScanResult() {
        return ScanResponseDTO.ScanResult.builder()
                .success(true)
                .confidence("high")
                .medications(List.of(
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("아스피린프로텍트100mg")
                                .drugItemSeq("200003933")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.MORNING, MedicationTiming.EVENING))
                                .durationDays(30)
                                .totalCount(60)
                                .entpName("바이엘코리아(주)")
                                .build(),
                        ScanResponseDTO.ScannedMedication.builder()
                                .name("메트포민500mg")
                                .dosage(1)
                                .frequency(2)
                                .timings(List.of(MedicationTiming.MORNING, MedicationTiming.EVENING))
                                .durationDays(30)
                                .totalCount(60)
                                .build()
                ))
                .notes(null)
                .build();
    }
}
