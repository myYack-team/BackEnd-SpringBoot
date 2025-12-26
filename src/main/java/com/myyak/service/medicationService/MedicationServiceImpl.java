package com.myyak.service.medicationService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.MedicationConverter;
import com.myyak.converter.ReminderConverter;
import com.myyak.domain.DrugInfo;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.repository.UserMedicationRepository;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.MedicationDTO.MedicationRequestDTO;
import com.myyak.web.dto.MedicationDTO.MedicationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationServiceImpl implements MedicationService {

    private final UserMedicationRepository userMedicationRepository;
    private final DrugInfoRepository drugInfoRepository;
    private final ReminderRepository reminderRepository;
    private final UserService userService;

    @Override
    public UserMedication findById(Long userMedicationId) {
        return userMedicationRepository.findById(userMedicationId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEDICATION_NOT_FOUND));
    }

    @Override
    @Transactional
    public MedicationResponseDTO.CreateResult createMedication(Long userId, MedicationRequestDTO.CreateRequest request) {
        User user = userService.findById(userId);

        // DrugInfo 조회 (drugItemSeq가 있으면 API 약물 정보 사용)
        DrugInfo drugInfo = null;
        if (request.getDrugItemSeq() != null && !request.getDrugItemSeq().isEmpty()) {
            drugInfo = drugInfoRepository.findById(request.getDrugItemSeq()).orElse(null);
        }

        UserMedication userMedication = MedicationConverter.toEntity(request, user, drugInfo);
        userMedicationRepository.save(userMedication);

        // 리마인더 생성
        List<MedicationTiming> timings = request.getTimings();
        for (MedicationTiming timing : timings) {
            if (timing != MedicationTiming.AS_NEEDED && timing.getDefaultTime() != null) {
                Reminder reminder = ReminderConverter.toEntity(userMedication, timing);
                reminderRepository.save(reminder);
            }
        }

        return MedicationConverter.toCreateResult(userMedication, timings);
    }

    @Override
    public MedicationResponseDTO.MedicationList getMedications(Long userId) {
        User user = userService.findById(userId);
        List<UserMedication> medications = userMedicationRepository.findByUserWithDrugInfo(user);

        // N+1 방지: 모든 리마인더를 한 번에 조회 후 Map으로 그룹화
        List<Reminder> allReminders = medications.isEmpty()
                ? List.of()
                : reminderRepository.findByUserMedicationIn(medications);
        Map<Long, List<Reminder>> remindersMap = allReminders.stream()
                .collect(Collectors.groupingBy(r -> r.getUserMedication().getId()));

        return MedicationConverter.toList(medications, remindersMap);
    }

    @Override
    public MedicationResponseDTO.MedicationDetail getMedicationDetail(Long userId, Long medicationId) {
        User user = userService.findById(userId);
        UserMedication medication = findById(medicationId);

        validateMedicationOwner(medication, user);

        List<Reminder> reminders = reminderRepository.findByUserMedication(medication);
        return MedicationConverter.toDetail(medication, reminders);
    }

    @Override
    @Transactional
    public MedicationResponseDTO.UpdateResult updateMedication(Long userId, Long medicationId, MedicationRequestDTO.UpdateRequest request) {
        User user = userService.findById(userId);
        UserMedication medication = findById(medicationId);

        validateMedicationOwner(medication, user);

        // 업데이트 값 처리
        String dosage = request.getDosage() != null ? request.getDosage() + "정" : medication.getDosage();
        Integer frequency = request.getFrequency() != null ? request.getFrequency() : medication.getFrequency();
        Integer totalCount = request.getTotalCount() != null ? request.getTotalCount() : medication.getTotalCount();
        Integer remainingCount = request.getRemainingCount() != null ? request.getRemainingCount() : medication.getRemainingCount();
        String memo = request.getMemo() != null ? request.getMemo() : medication.getMemo();

        medication.updateMedication(dosage, frequency,
                medication.getDurationDays(), totalCount, remainingCount, memo);

        List<MedicationTiming> timings;
        if (request.getTimings() != null) {
            // 기존 리마인더 삭제 후 새로 생성
            reminderRepository.deleteByUserMedication(medication);

            for (MedicationTiming timing : request.getTimings()) {
                if (timing != MedicationTiming.AS_NEEDED && timing.getDefaultTime() != null) {
                    Reminder reminder = ReminderConverter.toEntity(medication, timing);
                    reminderRepository.save(reminder);
                }
            }
            timings = request.getTimings();
        } else {
            List<Reminder> reminders = reminderRepository.findByUserMedication(medication);
            timings = reminders.stream()
                    .map(Reminder::getTiming)
                    .collect(Collectors.toList());
        }

        return MedicationConverter.toUpdateResult(medication, timings);
    }

    @Override
    @Transactional
    public void deleteMedication(Long userId, Long medicationId) {
        User user = userService.findById(userId);
        UserMedication medication = findById(medicationId);

        validateMedicationOwner(medication, user);

        medication.deactivate();
    }

    @Override
    @Transactional
    public MedicationResponseDTO.BatchDeleteResult deleteMedicationsBatch(Long userId, List<Long> ids) {
        User user = userService.findById(userId);

        // 사용자 소유의 활성 약물만 조회
        List<UserMedication> medications = userMedicationRepository.findAllById(ids).stream()
                .filter(med -> med.getUser().getId().equals(user.getId()))
                .filter(UserMedication::getIsActive)
                .toList();

        // 비활성화 처리
        medications.forEach(UserMedication::deactivate);

        return MedicationResponseDTO.BatchDeleteResult.builder()
                .requestedCount(ids.size())
                .deletedCount(medications.size())
                .build();
    }

    @Override
    public MedicationResponseDTO.DuplicateCheckResult checkDuplicates(Long userId, List<String> drugItemSeqs) {
        User user = userService.findById(userId);

        // 사용자의 활성 약물 중 drugItemSeq가 일치하는 것 찾기
        List<UserMedication> userMedications = userMedicationRepository.findByUserWithDrugInfo(user);

        List<MedicationResponseDTO.DuplicateMedication> duplicates = drugItemSeqs.stream()
                .flatMap(itemSeq -> userMedications.stream()
                        .filter(med -> med.getDrugInfo() != null &&
                                itemSeq.equals(med.getDrugInfo().getItemSeq()))
                        .map(med -> {
                            // 남은 일수 계산
                            int daysLeft = calculateDaysLeft(med);
                            return MedicationResponseDTO.DuplicateMedication.builder()
                                    .drugItemSeq(itemSeq)
                                    .drugName(med.getDrugName())
                                    .existingMedicationId(med.getId())
                                    .remainingCount(med.getRemainingCount())
                                    .daysLeft(daysLeft)
                                    .build();
                        }))
                .collect(Collectors.toList());

        return MedicationResponseDTO.DuplicateCheckResult.builder()
                .duplicates(duplicates)
                .duplicateCount(duplicates.size())
                .build();
    }

    /**
     * 남은 복용 일수 계산
     */
    private int calculateDaysLeft(UserMedication med) {
        if (med.getRemainingCount() == null || med.getRemainingCount() <= 0) {
            return 0;
        }
        if (med.getFrequency() == null || med.getFrequency() <= 0) {
            return med.getRemainingCount();
        }
        int dosagePerTime = 1;
        if (med.getDosage() != null && !med.getDosage().isEmpty()) {
            try {
                dosagePerTime = Integer.parseInt(med.getDosage().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                dosagePerTime = 1;
            }
        }
        int dailyUsage = med.getFrequency() * dosagePerTime;
        return (int) Math.ceil((double) med.getRemainingCount() / dailyUsage);
    }

    private void validateMedicationOwner(UserMedication medication, User user) {
        if (!medication.getUser().getId().equals(user.getId())) {
            throw new GeneralException(ErrorStatus.MEDICATION_ACCESS_DENIED);
        }
    }
}
