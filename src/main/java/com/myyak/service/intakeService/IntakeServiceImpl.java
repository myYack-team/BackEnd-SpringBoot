package com.myyak.service.intakeService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.IntakeConverter;
import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.IntakeDayStatus;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.IntakeRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.repository.UserMedicationRepository;
import com.myyak.service.userService.UserService;
import com.myyak.util.MedicationCalculator;
import com.myyak.web.dto.IntakeDTO.IntakeRequestDTO;
import com.myyak.web.dto.IntakeDTO.IntakeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeServiceImpl implements IntakeService {

    private final IntakeRepository intakeRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final ReminderRepository reminderRepository;
    private final UserService userService;

    @Override
    @Transactional
    public IntakeResponseDTO.CreateResult createIntake(Long userId, IntakeRequestDTO.CreateRequest request) {
        userService.findById(userId);

        MedicationTiming timing = request.getTiming();
        IntakeStatus status = request.getStatus() != null ? request.getStatus() : IntakeStatus.TAKEN;

        // 대상 약물 일괄 조회 후 존재/소유권 검증
        List<UserMedication> medications = userMedicationRepository.findAllById(request.getMedicationIds());
        if (medications.size() != request.getMedicationIds().size()) {
            throw new GeneralException(ErrorStatus.MEDICATION_NOT_FOUND);
        }
        boolean allOwned = medications.stream()
                .allMatch(m -> m.getUser().getId().equals(userId));
        if (!allOwned) {
            throw new GeneralException(ErrorStatus.MEDICATION_ACCESS_DENIED);
        }

        List<Intake> intakes = new ArrayList<>();
        for (UserMedication medication : medications) {
            Intake intake = IntakeConverter.toEntity(medication, timing, request.getTakenAt(), status);
            intakes.add(intake);

            // TAKEN 상태일 때만 남은 개수 차감
            if (status == IntakeStatus.TAKEN) {
                int dosage = MedicationCalculator.parseDosage(medication.getDosage());
                medication.decreaseRemainingCount(dosage);
                if (medication.getRemainingCount() <= 0) {
                    medication.completeOn(request.getTakenAt().toLocalDate());
                }
            }
        }

        // 배치 저장 (개별 save 대신 saveAll 사용)
        intakeRepository.saveAll(intakes);

        return IntakeConverter.toCreateResult(intakes, medications);
    }

    @Override
    public IntakeResponseDTO.DailyIntakeResult getIntakes(Long userId, LocalDate date) {
        userService.findById(userId);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 약물 + 영양제 리마인더 통합 조회 (날짜 기준 필터링)
        List<Reminder> allReminders = reminderRepository.findAllEnabledByUserIdWithDetailsIncludingInactive(userId);
        List<Reminder> reminders = allReminders.stream()
                .filter(r -> r.isScheduledOn(date))
                .collect(Collectors.toList());

        // 약물 + 영양제 복용 기록 통합 조회
        List<Intake> intakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(userId, startOfDay, endOfDay);

        // 약물 복용 기록: medicationId -> List<Intake>
        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .filter(Intake::isMedicationIntake)
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        // 영양제 복용 기록: supplementId -> List<Intake>
        Map<Long, List<Intake>> intakesBySupplementId = intakes.stream()
                .filter(Intake::isSupplementIntake)
                .collect(Collectors.groupingBy(i -> i.getUserSupplement().getId()));

        return IntakeConverter.toDailyResult(date, reminders, intakesByMedicationId, intakesBySupplementId);
    }

    @Override
    public IntakeResponseDTO.MonthlySummaryResult getMonthlySummary(Long userId, int year, int month) {
        userService.findById(userId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // 약물 + 영양제 리마인더 통합 조회
        List<Reminder> reminders = reminderRepository.findAllEnabledByUserIdWithDetailsIncludingInactive(userId);

        // 해당 월의 모든 복약 기록 가져오기 (약물 + 영양제)
        LocalDateTime monthStart = startDate.atStartOfDay();
        LocalDateTime monthEnd = endDate.atTime(LocalTime.MAX);
        List<Intake> monthlyIntakes = intakeRepository.findAllByUserIdAndDateRangeWithDetails(userId, monthStart, monthEnd);

        // 날짜별로 복약 기록 그룹화
        Map<LocalDate, List<Intake>> intakesByDate = monthlyIntakes.stream()
                .collect(Collectors.groupingBy(i -> i.getTakenAt().toLocalDate()));

        // 각 날짜별 요약 생성
        List<IntakeResponseDTO.DaySummaryItem> days = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 해당 날짜에 활성화된 리마인더의 총 약품 + 영양제 수 계산
            int totalScheduled = calculateTotalScheduledForDate(reminders, date);

            // 해당 날짜의 복약 기록 수
            List<Intake> dayIntakes = intakesByDate.getOrDefault(date, new ArrayList<>());
            int totalTaken = dayIntakes.size();

            // 상태 결정
            String status = determineStatus(totalScheduled, totalTaken, date, today);

            days.add(IntakeResponseDTO.DaySummaryItem.builder()
                    .date(date.toString())
                    .totalScheduled(totalScheduled)
                    .totalTaken(totalTaken)
                    .status(status)
                    .build());
        }

        return IntakeResponseDTO.MonthlySummaryResult.builder()
                .year(year)
                .month(month)
                .days(days)
                .build();
    }

    /**
     * 해당 날짜에 예정된 복약 횟수 계산 (약물 + 영양제)
     */
    private int calculateTotalScheduledForDate(List<Reminder> reminders, LocalDate date) {
        return (int) reminders.stream()
                .filter(Reminder::getEnabled)
                .filter(r -> r.isScheduledOn(date))
                .count();
    }

    private String determineStatus(int totalScheduled, int totalTaken, LocalDate date, LocalDate today) {
        if (totalScheduled == 0) {
            return IntakeDayStatus.NONE.name();
        }

        if (date.isAfter(today)) {
            return IntakeDayStatus.PENDING.name();
        }

        if (totalTaken >= totalScheduled) {
            return IntakeDayStatus.COMPLETE.name();
        }

        if (totalTaken > 0) {
            return IntakeDayStatus.PARTIAL.name();
        }

        if (date.isBefore(today)) {
            return IntakeDayStatus.MISSED.name();
        }

        return IntakeDayStatus.PENDING.name();
    }
}
