package com.myyak.service.prescriptionService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.ReminderConverter;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.Prescription;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.PrescriptionRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.repository.UserMedicationRepository;
import com.myyak.repository.UserRepository;
import com.myyak.util.FileUploadUtil;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionRequestDTO;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugInfoRepository drugInfoRepository;
    private final ReminderRepository reminderRepository;
    private final FileUploadUtil fileUploadUtil;

    @Override
    public PrescriptionResponseDTO.UploadResult uploadPrescription(Long userId, MultipartFile file, LocalDate prescriptionDate) {
        User user = findUserById(userId);

        // 파일 업로드
        String imageUrl = fileUploadUtil.uploadPrescriptionImage(file, userId);

        // 처방 날짜 (없으면 오늘)
        LocalDate date = prescriptionDate != null ? prescriptionDate : LocalDate.now();

        // Prescription 저장
        Prescription prescription = Prescription.create(user, imageUrl, date);
        prescriptionRepository.save(prescription);

        log.info("Prescription uploaded: userId={}, prescriptionId={}", userId, prescription.getId());

        return PrescriptionResponseDTO.UploadResult.builder()
                .prescriptionId(prescription.getId())
                .imageUrl(imageUrl)
                .prescriptionDate(date)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponseDTO.PrescriptionList getPrescriptionList(Long userId) {
        List<Prescription> prescriptions = prescriptionRepository.findByUserId(userId);

        List<PrescriptionResponseDTO.PrescriptionInfo> prescriptionInfos = prescriptions.stream()
                .map(p -> {
                    // 연결된 약품 수 계산
                    int medicationCount = userMedicationRepository.countByPrescriptionId(p.getId());

                    return PrescriptionResponseDTO.PrescriptionInfo.builder()
                            .id(p.getId())
                            .imageUrl(p.getImageUrl())
                            .prescriptionDate(p.getPrescriptionDate())
                            .patientName(p.getPatientName())
                            .hospitalName(p.getHospitalName())
                            .diagnosis(p.getDiagnosis())
                            .durationDays(p.getDurationDays())
                            .endDate(p.getEndDate())
                            .status(p.getStatus())
                            .notes(p.getNotes())
                            .medicationCount(medicationCount)
                            .createdAt(p.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return PrescriptionResponseDTO.PrescriptionList.builder()
                .prescriptions(prescriptionInfos)
                .totalCount(prescriptionInfos.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponseDTO.PrescriptionDetail getPrescriptionDetail(Long userId, Long prescriptionId) {
        Prescription prescription = findPrescriptionByIdAndUser(prescriptionId, userId);

        // 연결된 약품 목록
        List<UserMedication> medications = userMedicationRepository.findByPrescriptionId(prescriptionId);

        List<PrescriptionResponseDTO.MedicationSummary> medicationSummaries = medications.stream()
                .map(m -> PrescriptionResponseDTO.MedicationSummary.builder()
                        .id(m.getId())
                        .drugName(m.getDrugName())
                        .imageUrl(m.getDrugInfo() != null ? m.getDrugInfo().getImageUrl() : null)
                        .dosage(m.getDosage())
                        .frequency(m.getFrequency())
                        .build())
                .collect(Collectors.toList());

        return PrescriptionResponseDTO.PrescriptionDetail.builder()
                .id(prescription.getId())
                .imageUrl(prescription.getImageUrl())
                .prescriptionDate(prescription.getPrescriptionDate())
                .patientName(prescription.getPatientName())
                .hospitalName(prescription.getHospitalName())
                .diagnosis(prescription.getDiagnosis())
                .durationDays(prescription.getDurationDays())
                .endDate(prescription.getEndDate())
                .status(prescription.getStatus())
                .notes(prescription.getNotes())
                .medications(medicationSummaries)
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    @Override
    public PrescriptionResponseDTO.PrescriptionInfo updatePrescription(Long userId, Long prescriptionId, PrescriptionRequestDTO.UpdateRequest request) {
        Prescription prescription = findPrescriptionByIdAndUser(prescriptionId, userId);

        prescription.update(
                request.getPrescriptionDate(),
                request.getPatientName(),
                request.getHospitalName(),
                request.getDiagnosis(),
                request.getDurationDays(),
                request.getNotes()
        );

        int medicationCount = userMedicationRepository.countByPrescriptionId(prescriptionId);

        return PrescriptionResponseDTO.PrescriptionInfo.builder()
                .id(prescription.getId())
                .imageUrl(prescription.getImageUrl())
                .prescriptionDate(prescription.getPrescriptionDate())
                .patientName(prescription.getPatientName())
                .hospitalName(prescription.getHospitalName())
                .diagnosis(prescription.getDiagnosis())
                .durationDays(prescription.getDurationDays())
                .endDate(prescription.getEndDate())
                .status(prescription.getStatus())
                .notes(prescription.getNotes())
                .medicationCount(medicationCount)
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    @Override
    public void deletePrescription(Long userId, Long prescriptionId) {
        Prescription prescription = findPrescriptionByIdAndUser(prescriptionId, userId);

        // 연결된 약품들의 prescriptionId를 null로 설정
        List<UserMedication> medications = userMedicationRepository.findByPrescriptionId(prescriptionId);
        medications.forEach(m -> m.setPrescriptionId(null));

        // 이미지 파일 삭제
        fileUploadUtil.deleteFile(prescription.getImageUrl());

        // 처방전 삭제
        prescriptionRepository.delete(prescription);

        log.info("Prescription deleted: userId={}, prescriptionId={}", userId, prescriptionId);
    }

    @Override
    public PrescriptionResponseDTO.RegisterResult registerPrescription(Long userId, MultipartFile file, PrescriptionRequestDTO.RegisterRequest request) {
        User user = findUserById(userId);

        // 1. 파일 업로드
        String imageUrl = fileUploadUtil.uploadPrescriptionImage(file, userId);

        // 2. 처방 날짜 (없으면 오늘)
        LocalDate prescriptionDate = request.getPrescriptionDate() != null ? request.getPrescriptionDate() : LocalDate.now();

        // 3. Prescription 저장
        Prescription prescription = Prescription.create(user, imageUrl, prescriptionDate);
        prescription.update(
                prescriptionDate,
                request.getPatientName(),
                request.getHospitalName(),
                request.getDiagnosis(),
                request.getDurationDays(),
                request.getNotes()
        );
        prescriptionRepository.save(prescription);

        // 4. 약물들 일괄 등록
        List<PrescriptionResponseDTO.RegisteredMedication> registeredMedications = new ArrayList<>();

        for (PrescriptionRequestDTO.MedicationInfo medInfo : request.getMedications()) {
            // DrugInfo 조회
            DrugInfo drugInfo = null;
            if (medInfo.getDrugItemSeq() != null && !medInfo.getDrugItemSeq().isEmpty()) {
                drugInfo = drugInfoRepository.findById(medInfo.getDrugItemSeq()).orElse(null);
            }

            // UserMedication 생성
            UserMedication medication = UserMedication.builder()
                    .user(user)
                    .drugInfo(drugInfo)
                    .customDrugName(drugInfo == null ? medInfo.getCustomDrugName() : null)
                    .dosage(medInfo.getDosage() + "정")
                    .frequency(medInfo.getFrequency())
                    .durationDays(medInfo.getDurationDays())
                    .totalCount(medInfo.getTotalCount())
                    .remainingCount(medInfo.getTotalCount())
                    .startDate(medInfo.getStartDate())
                    .endDate(medInfo.getStartDate().plusDays(medInfo.getDurationDays()))
                    .isActive(true)
                    .memo(medInfo.getMemo())
                    .prescriptionId(prescription.getId())
                    .build();
            userMedicationRepository.save(medication);

            // 리마인더 생성 (커스텀 시간 사용)
            for (PrescriptionRequestDTO.TimingWithTime twt : medInfo.getTimings()) {
                MedicationTiming timing = twt.getTiming();
                if (timing != MedicationTiming.AS_NEEDED && twt.getTime() != null) {
                    Reminder reminder = ReminderConverter.toEntity(medication, timing, twt.getTime());
                    reminderRepository.save(reminder);
                }
            }

            // 결과 DTO 생성
            registeredMedications.add(PrescriptionResponseDTO.RegisteredMedication.builder()
                    .id(medication.getId())
                    .drugName(medication.getDrugName())
                    .dosage(medInfo.getDosage())
                    .frequency(medInfo.getFrequency())
                    .durationDays(medInfo.getDurationDays())
                    .build());
        }

        log.info("Prescription registered: userId={}, prescriptionId={}, medicationCount={}",
                userId, prescription.getId(), registeredMedications.size());

        return PrescriptionResponseDTO.RegisterResult.builder()
                .prescriptionId(prescription.getId())
                .imageUrl(imageUrl)
                .prescriptionDate(prescriptionDate)
                .medications(registeredMedications)
                .totalMedicationCount(registeredMedications.size())
                .build();
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));
    }

    private Prescription findPrescriptionByIdAndUser(Long prescriptionId, Long userId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));

        if (!prescription.getUser().getId().equals(userId)) {
            throw new GeneralException(ErrorStatus._FORBIDDEN);
        }

        return prescription;
    }
}
