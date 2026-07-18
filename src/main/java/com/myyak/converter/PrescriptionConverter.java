package com.myyak.converter;

import com.myyak.domain.Prescription;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.PrescriptionStatus;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionRequestDTO;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionResponseDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class PrescriptionConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 처방전 업로드 결과 변환
     */
    public static PrescriptionResponseDTO.UploadResult toUploadResult(Prescription prescription) {
        return PrescriptionResponseDTO.UploadResult.builder()
                .prescriptionId(prescription.getId())
                .imageUrl(prescription.getImageUrl())
                .prescriptionDate(prescription.getPrescriptionDate())
                .build();
    }

    /**
     * 처방전 요약 정보 변환 (목록/수정 응답용)
     */
    public static PrescriptionResponseDTO.PrescriptionInfo toPrescriptionInfo(
            Prescription prescription, int medicationCount, PrescriptionStatus status) {
        return PrescriptionResponseDTO.PrescriptionInfo.builder()
                .id(prescription.getId())
                .imageUrl(prescription.getImageUrl())
                .prescriptionDate(prescription.getPrescriptionDate())
                .patientName(prescription.getPatientName())
                .hospitalName(prescription.getHospitalName())
                .doctorName(prescription.getDoctorName())
                .diagnosis(prescription.getDiagnosis())
                .durationDays(prescription.getDurationDays())
                .endDate(prescription.getEndDate())
                .status(status.name())
                .notes(prescription.getNotes())
                .medicationCount(medicationCount)
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    /**
     * 처방전 목록 변환
     */
    public static PrescriptionResponseDTO.PrescriptionList toPrescriptionList(
            List<PrescriptionResponseDTO.PrescriptionInfo> prescriptionInfos) {
        return PrescriptionResponseDTO.PrescriptionList.builder()
                .prescriptions(prescriptionInfos)
                .totalCount(prescriptionInfos.size())
                .build();
    }

    /**
     * 리마인더 정보 변환
     */
    public static PrescriptionResponseDTO.ReminderInfo toReminderInfo(Reminder reminder) {
        return PrescriptionResponseDTO.ReminderInfo.builder()
                .id(reminder.getId())
                .time(reminder.getTime().format(TIME_FORMATTER))
                .enabled(reminder.getEnabled())
                .build();
    }

    /**
     * 처방전 상세의 약물 요약 변환
     */
    public static PrescriptionResponseDTO.MedicationSummary toMedicationSummary(
            UserMedication medication, List<Reminder> reminders, String imageUrl, int daysLeft) {
        List<PrescriptionResponseDTO.ReminderInfo> reminderInfos = reminders.stream()
                .map(PrescriptionConverter::toReminderInfo)
                .collect(Collectors.toList());

        return PrescriptionResponseDTO.MedicationSummary.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .displayName(medication.getDrugName())
                .imageUrl(imageUrl)
                .dosage(medication.getDosage())
                .frequency(medication.getFrequency())
                .durationDays(medication.getDurationDays())
                .remainingCount(medication.getRemainingCount())
                .daysLeft(daysLeft)
                .reminders(reminderInfos)
                .build();
    }

    /**
     * 처방전 상세 정보 변환
     */
    public static PrescriptionResponseDTO.PrescriptionDetail toPrescriptionDetail(
            Prescription prescription, PrescriptionStatus status,
            List<PrescriptionResponseDTO.MedicationSummary> medications) {
        return PrescriptionResponseDTO.PrescriptionDetail.builder()
                .id(prescription.getId())
                .imageUrl(prescription.getImageUrl())
                .prescriptionDate(prescription.getPrescriptionDate())
                .patientName(prescription.getPatientName())
                .hospitalName(prescription.getHospitalName())
                .doctorName(prescription.getDoctorName())
                .diagnosis(prescription.getDiagnosis())
                .durationDays(prescription.getDurationDays())
                .endDate(prescription.getEndDate())
                .status(status.name())
                .notes(prescription.getNotes())
                .medications(medications)
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    /**
     * 등록된 약물 정보 변환 (일괄 등록 결과용)
     */
    public static PrescriptionResponseDTO.RegisteredMedication toRegisteredMedication(
            UserMedication medication, PrescriptionRequestDTO.MedicationInfo medInfo) {
        return PrescriptionResponseDTO.RegisteredMedication.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .dosage(medInfo.getDosage())
                .frequency(medInfo.getFrequency())
                .durationDays(medInfo.getDurationDays())
                .build();
    }

    /**
     * 처방전 + 약물 일괄 등록 결과 변환
     */
    public static PrescriptionResponseDTO.RegisterResult toRegisterResult(
            Prescription prescription, String imageUrl, LocalDate prescriptionDate,
            List<PrescriptionResponseDTO.RegisteredMedication> medications) {
        return PrescriptionResponseDTO.RegisterResult.builder()
                .prescriptionId(prescription.getId())
                .imageUrl(imageUrl)
                .prescriptionDate(prescriptionDate)
                .medications(medications)
                .totalMedicationCount(medications.size())
                .build();
    }

    /**
     * 처방전 일괄 삭제 결과 변환
     */
    public static PrescriptionResponseDTO.BatchDeleteResult toBatchDeleteResult(
            int requestedCount, int deletedCount, List<Long> failedIds) {
        return PrescriptionResponseDTO.BatchDeleteResult.builder()
                .requestedCount(requestedCount)
                .deletedCount(deletedCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds.isEmpty() ? null : failedIds)
                .build();
    }
}
