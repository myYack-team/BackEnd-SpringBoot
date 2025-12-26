package com.myyak.service.intakeService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.IntakeConverter;
import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.repository.IntakeRepository;
import com.myyak.repository.ReminderRepository;
import com.myyak.repository.UserMedicationRepository;
import com.myyak.service.userService.UserService;
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
    public IntakeResponseDTO.CreateResult recordIntake(Long userId, IntakeRequestDTO.CreateRequest request) {
        userService.findById(userId);

        List<Intake> intakes = new ArrayList<>();
        List<UserMedication> medications = new ArrayList<>();

        MedicationTiming timing = request.getTiming();

        IntakeStatus status = request.getStatus() != null ? request.getStatus() : IntakeStatus.TAKEN;

        for (Long medicationId : request.getMedicationIds()) {
            UserMedication medication = userMedicationRepository.findById(medicationId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.MEDICATION_NOT_FOUND));

            if (!medication.getUser().getId().equals(userId)) {
                throw new GeneralException(ErrorStatus.MEDICATION_ACCESS_DENIED);
            }

            Intake intake = IntakeConverter.toEntity(medication, timing, request.getTakenAt(), status);
            intakes.add(intake);

            // TAKEN 상태일 때만 남은 개수 차감
            if (status == IntakeStatus.TAKEN) {
                String dosageStr = medication.getDosage().replaceAll("[^0-9]", "");
                int dosage = dosageStr.isEmpty() ? 1 : Integer.parseInt(dosageStr);
                medication.decreaseRemainingCount(dosage);
            }
            medications.add(medication);
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

        // N+1 방지: Fetch Join으로 UserMedication 함께 조회
        List<Reminder> reminders = reminderRepository.findEnabledByUserIdWithMedication(userId);
        List<Intake> intakes = intakeRepository.findByUserIdAndDateRangeWithMedication(userId, startOfDay, endOfDay);

        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        return IntakeConverter.toDailyResult(date, reminders, intakesByMedicationId);
    }

    @Override
    public IntakeResponseDTO.MonthlySummaryResult getMonthlySummary(Long userId, int year, int month) {
        userService.findById(userId);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        // 해당 월의 모든 리마인더 가져오기 (N+1 방지)
        List<Reminder> reminders = reminderRepository.findEnabledByUserIdWithMedication(userId);

        // 해당 월의 모든 복약 기록 가져오기 (N+1 방지)
        LocalDateTime monthStart = startDate.atStartOfDay();
        LocalDateTime monthEnd = endDate.atTime(LocalTime.MAX);
        List<Intake> monthlyIntakes = intakeRepository.findByUserIdAndDateRangeWithMedication(userId, monthStart, monthEnd);

        // 날짜별로 복약 기록 그룹화
        Map<LocalDate, List<Intake>> intakesByDate = monthlyIntakes.stream()
                .collect(Collectors.groupingBy(i -> i.getTakenAt().toLocalDate()));

        // 각 날짜별 요약 생성
        List<IntakeResponseDTO.DaySummaryItem> days = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 해당 날짜에 활성화된 리마인더의 총 약품 수 계산
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

    private int calculateTotalScheduledForDate(List<Reminder> reminders, LocalDate date) {
        // 각 리마인더별로 해당 날짜에 예정된 복약 횟수 합산
        // 리마인더는 각 타이밍별로 하나씩 있으므로 활성화된 리마인더 수 = 스케줄 수
        return (int) reminders.stream()
                .filter(r -> r.getEnabled() && r.getUserMedication().getIsActive())
                .filter(r -> {
                    LocalDate startDate = r.getUserMedication().getStartDate();
                    LocalDate endDate = r.getUserMedication().getEndDate();
                    return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
                })
                .count();
    }

    private String determineStatus(int totalScheduled, int totalTaken, LocalDate date, LocalDate today) {
        if (totalScheduled == 0) {
            return "NONE";  // 예정된 복약 없음
        }

        if (date.isAfter(today)) {
            return "PENDING";  // 미래 날짜
        }

        if (totalTaken >= totalScheduled) {
            return "COMPLETE";  // 모든 약 복용 완료
        }

        if (totalTaken > 0) {
            return "PARTIAL";  // 일부만 복용
        }

        if (date.isBefore(today)) {
            return "MISSED";  // 과거인데 복용 안 함
        }

        return "PENDING";  // 오늘인데 아직 복용 전
    }
}
