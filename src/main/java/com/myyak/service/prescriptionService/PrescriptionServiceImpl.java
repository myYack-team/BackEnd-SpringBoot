package com.myyak.service.prescriptionService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.PrescriptionConverter;
import com.myyak.converter.ReminderConverter;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.Prescription;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.PrescriptionStatus;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugInfoRepository drugInfoRepository;
    private final ReminderRepository reminderRepository;
    private final FileUploadUtil fileUploadUtil;

    @Override
    @Transactional
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

        return PrescriptionConverter.toUploadResult(prescription);
    }

    @Override
    public PrescriptionResponseDTO.PrescriptionList getPrescriptionList(Long userId) {
        List<Prescription> prescriptions = prescriptionRepository.findByUserId(userId);

        if (prescriptions.isEmpty()) {
            return PrescriptionConverter.toPrescriptionList(List.of());
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

                    // 연결된 약물 중 최대 복용일수를 기준으로 복용 상태 계산
                    PrescriptionStatus status = calculateStatus(p.getPrescriptionDate(), medications);

                    return PrescriptionConverter.toPrescriptionInfo(p, medications.size(), status);
                })
                .collect(Collectors.toList());

        return PrescriptionConverter.toPrescriptionList(prescriptionInfos);
    }

    /**
     * 처방전의 복용 상태 계산
     * 연결된 약물 중 최대 복용일수를 기준으로 판단
     */
    private PrescriptionStatus calculateStatus(LocalDate prescriptionDate, List<UserMedication> medications) {
        if (prescriptionDate == null) {
            return PrescriptionStatus.IN_PROGRESS;
        }

        LocalDate today = LocalDate.now();

        // 처방일이 미래인 경우
        if (today.isBefore(prescriptionDate)) {
            return PrescriptionStatus.UPCOMING;
        }

        // 연결된 약물이 없으면 처방전 자체의 durationDays 사용 불가 → 복용 중으로 처리
        if (medications.isEmpty()) {
            return PrescriptionStatus.IN_PROGRESS;
        }

        // 연결된 약물 중 최대 복용일수
        int maxDurationDays = medications.stream()
                .map(UserMedication::getDurationDays)
                .filter(d -> d != null && d > 0)
                .max(Integer::compareTo)
                .orElse(0);

        if (maxDurationDays == 0) {
            return PrescriptionStatus.IN_PROGRESS;
        }

        // 복용 종료일 = 처방일 + 최대 복용일수 - 1
        LocalDate endDate = prescriptionDate.plusDays(maxDurationDays - 1);

        if (today.isAfter(endDate)) {
            return PrescriptionStatus.COMPLETED;
        }

        return PrescriptionStatus.IN_PROGRESS;
    }

    @Override
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

                    // 남은 복용 일수 계산
                    int daysLeft = MedicationCalculator.calculateDaysLeft(
                            m.getRemainingCount(), m.getFrequency(), m.getDosage());

                    // imageUrl: 경량 DrugInfo 맵에서 조회
                    String imageUrl = null;
                    if (m.getDrugInfo() != null) {
                        Object[] summary = finalDrugInfoMap.get(m.getDrugInfo().getItemSeq());
                        if (summary != null) {
                            // [item_seq, item_name, display_name, ingredient_kr, entp_name, image_url, drug_type]
                            imageUrl = (String) summary[5];
                        }
                    }

                    return PrescriptionConverter.toMedicationSummary(m, reminders, imageUrl, daysLeft);
                })
                .collect(Collectors.toList());

        // 복용 상태 계산
        PrescriptionStatus status = calculateStatus(prescription.getPrescriptionDate(), medications);

        return PrescriptionConverter.toPrescriptionDetail(prescription, status, medicationSummaries);
    }


    @Override
    @Transactional
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
        PrescriptionStatus status = calculateStatus(prescription.getPrescriptionDate(), medications);

        return PrescriptionConverter.toPrescriptionInfo(prescription, medications.size(), status);
    }

    @Override
    @Transactional
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
    @Transactional
    public PrescriptionResponseDTO.RegisterResult createPrescription(Long userId, MultipartFile file, PrescriptionRequestDTO.RegisterRequest request) {
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

        // 4. 약물 엔티티 일괄 생성 후 saveAll (개별 save 대신 일괄 저장)
        List<PrescriptionRequestDTO.MedicationInfo> medicationInfos = request.getMedications();
        List<UserMedication> medications = new ArrayList<>();

        for (PrescriptionRequestDTO.MedicationInfo medInfo : medicationInfos) {
            // DrugInfo 조회
            DrugInfo drugInfo = null;
            if (medInfo.getDrugItemSeq() != null && !medInfo.getDrugItemSeq().isEmpty()) {
                drugInfo = drugInfoRepository.findById(medInfo.getDrugItemSeq()).orElse(null);
            }

            // UserMedication 생성
            medications.add(UserMedication.builder()
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
                    .build());
        }
        userMedicationRepository.saveAll(medications);

        // 5. 리마인더 일괄 생성 (약물 저장 이후 생성 - 연관관계 보장, 커스텀 시간 기준으로 timing 재계산)
        List<Reminder> reminders = new ArrayList<>();
        for (int i = 0; i < medications.size(); i++) {
            UserMedication medication = medications.get(i);
            for (PrescriptionRequestDTO.TimingWithTime twt : medicationInfos.get(i).getTimings()) {
                if (twt.getTime() != null) {
                    MedicationTiming timing = MedicationTiming.fromTime(twt.getTime());
                    if (timing != MedicationTiming.AS_NEEDED) {
                        reminders.add(ReminderConverter.toEntity(medication, timing, twt.getTime()));
                    }
                }
            }
        }
        reminderRepository.saveAll(reminders);

        // 6. 결과 DTO 생성
        List<PrescriptionResponseDTO.RegisteredMedication> registeredMedications = new ArrayList<>();
        for (int i = 0; i < medications.size(); i++) {
            registeredMedications.add(PrescriptionConverter.toRegisteredMedication(medications.get(i), medicationInfos.get(i)));
        }

        log.info("Prescription registered: userId={}, prescriptionId={}, medicationCount={}",
                userId, prescription.getId(), registeredMedications.size());

        return PrescriptionConverter.toRegisterResult(prescription, imageUrl, prescriptionDate, registeredMedications);
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
    @Transactional
    public PrescriptionResponseDTO.BatchDeleteResult deletePrescriptionsBatch(Long userId, List<Long> ids) {
        // 사용자 소유의 처방전만 조회 (소유권 검증을 쿼리 조건으로 수행)
        List<Prescription> prescriptions = prescriptionRepository.findByIdInAndUserId(ids, userId);

        // 파일 URL 목록 수집 (DB 삭제 전에 추출)
        List<String> imageUrls = prescriptions.stream()
                .map(Prescription::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();

        List<Long> prescriptionIdList = prescriptions.stream()
                .map(Prescription::getId)
                .toList();

        // 한 번에 모든 연관 약물 조회 (N+1 방지, DrugInfo join 제외 - prescriptionId 초기화 목적이므로 불필요)
        List<UserMedication> allMedications =
                userMedicationRepository.findByPrescriptionIdInLight(prescriptionIdList);
        allMedications.forEach(m -> m.setPrescriptionId(null));

        // 처방전 벌크 삭제 (단일 SQL DELETE IN 구문 - deleteAll 대비 N개의 쿼리 대신 1개)
        prescriptionRepository.deleteAllInBatch(prescriptions);

        // 이미지 파일 삭제 (트랜잭션 커밋 후 비동기 실행 - 롤백 시 파일 삭제 방지)
        if (!imageUrls.isEmpty()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fileUploadUtil.deleteFilesAsync(imageUrls);
                    }
                });
            } else {
                // 트랜잭션 컨텍스트 밖에서 호출되는 경우 (테스트 등) 즉시 실행
                fileUploadUtil.deleteFilesAsync(imageUrls);
            }
        }

        // 삭제 실패한 ID 목록 (요청 ID 중 삭제되지 않은 것)
        List<Long> failedIds = ids.stream()
                .filter(id -> !prescriptionIdList.contains(id))
                .toList();

        log.info("Prescriptions batch deleted: userId={}, requestedCount={}, deletedCount={}, failedCount={}",
                userId, ids.size(), prescriptions.size(), failedIds.size());

        return PrescriptionConverter.toBatchDeleteResult(ids.size(), prescriptions.size(), failedIds);
    }
}
