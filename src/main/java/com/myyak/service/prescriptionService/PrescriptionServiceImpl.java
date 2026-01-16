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
import com.myyak.util.MedicationCalculator;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionRequestDTO;
import com.myyak.web.dto.PrescriptionDTO.PrescriptionResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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

        if (prescriptions.isEmpty()) {
            return PrescriptionResponseDTO.PrescriptionList.builder()
                    .prescriptions(List.of())
                    .totalCount(0)
                    .build();
        }

        // N+1 방지: 모든 처방전의 약물을 한 번에 조회 (DrugInfo 제외 - 성능 최적화)
        List<Long> prescriptionIds = prescriptions.stream()
                .map(Prescription::getId)
                .collect(Collectors.toList());
        List<UserMedication> allMedications = userMedicationRepository.findByPrescriptionIdInLight(prescriptionIds);

        // 처방전 ID별로 약물 그룹화
        Map<Long, List<UserMedication>> medicationsMap = allMedications.stream()
                .collect(Collectors.groupingBy(UserMedication::getPrescriptionId));

        List<PrescriptionResponseDTO.PrescriptionInfo> prescriptionInfos = prescriptions.stream()
                .map(p -> {
                    // 그룹화된 맵에서 약물 목록 조회 (추가 쿼리 없음)
                    List<UserMedication> medications = medicationsMap.getOrDefault(p.getId(), List.of());
                    int medicationCount = medications.size();

                    // 연결된 약물 중 최대 복용일수를 기준으로 복용 상태 계산
                    String status = calculateStatus(p.getPrescriptionDate(), medications);

                    return PrescriptionResponseDTO.PrescriptionInfo.builder()
                            .id(p.getId())
                            .imageUrl(p.getImageUrl())
                            .prescriptionDate(p.getPrescriptionDate())
                            .patientName(p.getPatientName())
                            .hospitalName(p.getHospitalName())
                            .doctorName(p.getDoctorName())
                            .diagnosis(p.getDiagnosis())
                            .durationDays(p.getDurationDays())
                            .endDate(p.getEndDate())
                            .status(status)
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

    /**
     * 처방전의 복용 상태 계산
     * 연결된 약물 중 최대 복용일수를 기준으로 판단
     */
    private String calculateStatus(LocalDate prescriptionDate, List<UserMedication> medications) {
        if (prescriptionDate == null) {
            return "IN_PROGRESS";
        }

        LocalDate today = LocalDate.now();

        // 처방일이 미래인 경우
        if (today.isBefore(prescriptionDate)) {
            return "UPCOMING";
        }

        // 연결된 약물이 없으면 처방전 자체의 durationDays 사용 불가 → 복용 중으로 처리
        if (medications.isEmpty()) {
            return "IN_PROGRESS";
        }

        // 연결된 약물 중 최대 복용일수
        int maxDurationDays = medications.stream()
                .map(UserMedication::getDurationDays)
                .filter(d -> d != null && d > 0)
                .max(Integer::compareTo)
                .orElse(0);

        if (maxDurationDays == 0) {
            return "IN_PROGRESS";
        }

        // 복용 종료일 = 처방일 + 최대 복용일수 - 1
        LocalDate endDate = prescriptionDate.plusDays(maxDurationDays - 1);

        if (today.isAfter(endDate)) {
            return "COMPLETED";
        }

        return "IN_PROGRESS";
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionResponseDTO.PrescriptionDetail getPrescriptionDetail(Long userId, Long prescriptionId) {
        Prescription prescription = findPrescriptionByIdAndUser(prescriptionId, userId);

        // 연결된 약품 목록 (DrugInfo TEXT 컬럼 제외 - 성능 최적화)
        List<UserMedication> medications = userMedicationRepository.findByPrescriptionIdInLight(List.of(prescriptionId));

        // DrugInfo 경량 데이터 조회
        List<String> drugItemSeqs = medications.stream()
                .filter(m -> m.getDrugInfo() != null)
                .map(m -> m.getDrugInfo().getItemSeq())
                .distinct()
                .toList();

        Map<String, Object[]> drugInfoMap = Map.of();
        if (!drugItemSeqs.isEmpty()) {
            List<Object[]> drugSummaries = drugInfoRepository.findSummaryByItemSeqIn(drugItemSeqs);
            drugInfoMap = drugSummaries.stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],  // item_seq
                            row -> row
                    ));
        }
        final Map<String, Object[]> finalDrugInfoMap = drugInfoMap;

        // N+1 방지: 모든 리마인더를 한 번에 조회
        List<Reminder> allReminders = medications.isEmpty()
                ? List.of()
                : reminderRepository.findByUserMedicationIn(medications);
        Map<Long, List<Reminder>> remindersMap = allReminders.stream()
                .collect(Collectors.groupingBy(r -> r.getUserMedication().getId()));

        List<PrescriptionResponseDTO.MedicationSummary> medicationSummaries = medications.stream()
                .map(m -> {
                    List<Reminder> reminders = remindersMap.getOrDefault(m.getId(), List.of());
                    List<PrescriptionResponseDTO.ReminderInfo> reminderInfos = reminders.stream()
                            .map(r -> PrescriptionResponseDTO.ReminderInfo.builder()
                                    .id(r.getId())
                                    .time(r.getTime().format(TIME_FORMATTER))
                                    .enabled(r.getEnabled())
                                    .build())
                            .collect(Collectors.toList());

                    // 남은 복용 일수 계산
                    int daysLeft = MedicationCalculator.calculateDaysLeft(
                            m.getRemainingCount(), m.getFrequency(), m.getDosage());

                    // displayName과 imageUrl: 경량 DrugInfo 맵에서 조회
                    String displayName = m.getDrugName();
                    String imageUrl = null;
                    if (m.getDrugInfo() != null) {
                        Object[] summary = finalDrugInfoMap.get(m.getDrugInfo().getItemSeq());
                        if (summary != null) {
                            // [item_seq, item_name, display_name, ingredient_kr, entp_name, image_url, drug_type]
                            imageUrl = (String) summary[5];
                        }
                    }

                    return PrescriptionResponseDTO.MedicationSummary.builder()
                            .id(m.getId())
                            .drugName(m.getDrugName())
                            .displayName(displayName)
                            .imageUrl(imageUrl)
                            .dosage(m.getDosage())
                            .frequency(m.getFrequency())
                            .durationDays(m.getDurationDays())
                            .remainingCount(m.getRemainingCount())
                            .daysLeft(daysLeft)
                            .reminders(reminderInfos)
                            .build();
                })
                .collect(Collectors.toList());

        // 복용 상태 계산
        String status = calculateStatus(prescription.getPrescriptionDate(), medications);

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
                .status(status)
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
                request.getDoctorName(),
                request.getDiagnosis(),
                request.getDurationDays(),
                request.getNotes()
        );

        // 연결된 약물 목록 조회하여 상태 계산
        List<UserMedication> medications = userMedicationRepository.findByPrescriptionId(prescriptionId);
        String status = calculateStatus(prescription.getPrescriptionDate(), medications);

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
                .status(status)
                .notes(prescription.getNotes())
                .medicationCount(medications.size())
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    @Override
    public void deletePrescription(Long userId, Long prescriptionId) {
        Prescription prescription = findPrescriptionByIdAndUser(prescriptionId, userId);

        // 연결된 약품들의 prescriptionId를 null로 설정
        List<UserMedication> medications = userMedicationRepository.findByPrescriptionId(prescriptionId);
        medications.forEach(m -> m.setPrescriptionId(null));

        // 파일 URL 저장 (DB 삭제 전에 추출)
        String imageUrl = prescription.getImageUrl();

        // 처방전 삭제 (DB 먼저)
        prescriptionRepository.delete(prescription);

        // 이미지 파일 삭제 (비동기 - 백그라운드에서 처리)
        fileUploadUtil.deleteFileAsync(imageUrl);

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
        // 환자명은 로그인한 사용자 이름 사용 (스캔 시 마스킹된 값 대신)
        Prescription prescription = Prescription.create(user, imageUrl, prescriptionDate);
        prescription.update(
                prescriptionDate,
                user.getName(),
                request.getHospitalName(),
                null, // doctorName
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

    @Override
    public PrescriptionResponseDTO.BatchDeleteResult deletePrescriptionsBatch(Long userId, List<Long> ids) {
        // 사용자 소유의 처방전만 필터링
        List<Prescription> prescriptions = prescriptionRepository.findAllById(ids).stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .toList();

        // 파일 URL 목록 수집 (DB 삭제 전에 추출)
        List<String> imageUrls = prescriptions.stream()
                .map(Prescription::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();

        // 각 처방전에 대해 삭제 처리
        for (Prescription prescription : prescriptions) {
            // 연결된 약품들의 prescriptionId를 null로 설정
            List<UserMedication> medications = userMedicationRepository.findByPrescriptionId(prescription.getId());
            medications.forEach(m -> m.setPrescriptionId(null));

            // 처방전 삭제 (DB 먼저)
            prescriptionRepository.delete(prescription);
        }

        // 이미지 파일 삭제 (비동기 - 백그라운드에서 처리)
        fileUploadUtil.deleteFilesAsync(imageUrls);

        // 삭제된 ID 목록
        List<Long> deletedIds = prescriptions.stream()
                .map(Prescription::getId)
                .toList();

        // 삭제 실패한 ID 목록 (요청 ID 중 삭제되지 않은 것)
        List<Long> failedIds = ids.stream()
                .filter(id -> !deletedIds.contains(id))
                .toList();

        log.info("Prescriptions batch deleted: userId={}, requestedCount={}, deletedCount={}, failedCount={}",
                userId, ids.size(), prescriptions.size(), failedIds.size());

        return PrescriptionResponseDTO.BatchDeleteResult.builder()
                .requestedCount(ids.size())
                .deletedCount(prescriptions.size())
                .failedCount(failedIds.size())
                .failedIds(failedIds.isEmpty() ? null : failedIds)
                .build();
    }
}
