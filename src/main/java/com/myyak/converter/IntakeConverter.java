package com.myyak.converter;

import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.Supplement;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.IntakeStatus;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.IntakeDTO.IntakeResponseDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IntakeConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static Intake toEntity(UserMedication userMedication, MedicationTiming timing, LocalDateTime takenAt) {
        return toEntity(userMedication, timing, takenAt, IntakeStatus.TAKEN);
    }

    public static Intake toEntity(UserMedication userMedication, MedicationTiming timing, LocalDateTime takenAt, IntakeStatus status) {
        return Intake.builder()
                .userMedication(userMedication)
                .timing(timing)
                .takenAt(takenAt)
                .status(status)
                .build();
    }

    public static IntakeResponseDTO.IntakeItem toItem(Intake intake) {
        return IntakeResponseDTO.IntakeItem.builder()
                .id(intake.getId())
                .medicationId(intake.getUserMedication().getId())
                .medicationName(intake.getUserMedication().getDrugName())
                .takenAt(intake.getTakenAt())
                .status(intake.getStatus())
                .build();
    }

    public static IntakeResponseDTO.MedicationStockInfo toStockInfo(UserMedication userMedication) {
        boolean lowStock = userMedication.isLowStock();
        String message = lowStock ? "약이 3일치 남았어요" : null;

        return IntakeResponseDTO.MedicationStockInfo.builder()
                .id(userMedication.getId())
                .remainingCount(userMedication.getRemainingCount())
                .lowStock(lowStock)
                .lowStockMessage(message)
                .build();
    }

    public static IntakeResponseDTO.CreateResult toCreateResult(List<Intake> intakes, List<UserMedication> medications) {
        List<IntakeResponseDTO.IntakeItem> intakeItems = intakes.stream()
                .map(IntakeConverter::toItem)
                .collect(Collectors.toList());

        List<IntakeResponseDTO.MedicationStockInfo> stockInfos = medications.stream()
                .map(IntakeConverter::toStockInfo)
                .collect(Collectors.toList());

        return IntakeResponseDTO.CreateResult.builder()
                .intakes(intakeItems)
                .updatedMedications(stockInfos)
                .build();
    }

    /**
     * 일별 복용 결과 변환 (약물 + 영양제 통합)
     */
    public static IntakeResponseDTO.DailyIntakeResult toDailyResult(
            LocalDate date,
            List<Reminder> reminders,
            Map<Long, List<Intake>> intakesByMedicationId,
            Map<Long, List<Intake>> intakesBySupplementId) {

        Map<MedicationTiming, List<Reminder>> remindersByTiming = reminders.stream()
                .collect(Collectors.groupingBy(Reminder::getTiming));

        List<IntakeResponseDTO.ScheduleItem> schedules = new ArrayList<>();

        for (MedicationTiming timing : MedicationTiming.values()) {
            if (timing == MedicationTiming.AS_NEEDED) continue;

            List<Reminder> timingReminders = remindersByTiming.getOrDefault(timing, List.of());
            if (timingReminders.isEmpty()) continue;

            List<IntakeResponseDTO.ScheduleMedication> meds = timingReminders.stream()
                    .map(r -> convertReminderToScheduleMedication(r, date, timing, intakesByMedicationId, intakesBySupplementId))
                    .sorted(Comparator.comparing(IntakeResponseDTO.ScheduleMedication::getIsSupplement))
                    .collect(Collectors.toList());

            boolean allTaken = meds.stream().allMatch(IntakeResponseDTO.ScheduleMedication::getTaken);

            String scheduledTime = timingReminders.get(0).getTime().format(TIME_FORMATTER);

            schedules.add(IntakeResponseDTO.ScheduleItem.builder()
                    .timing(timing)
                    .timingLabel(timing.getDescription())
                    .scheduledTime(scheduledTime)
                    .medications(meds)
                    .allTaken(allTaken)
                    .build());
        }

        int totalScheduled = schedules.stream()
                .mapToInt(s -> s.getMedications().size())
                .sum();
        int totalTaken = schedules.stream()
                .mapToInt(s -> (int) s.getMedications().stream().filter(IntakeResponseDTO.ScheduleMedication::getTaken).count())
                .sum();
        double completionRate = totalScheduled > 0 ? (totalTaken * 100.0 / totalScheduled) : 0;

        return IntakeResponseDTO.DailyIntakeResult.builder()
                .date(date.format(DATE_FORMATTER))
                .schedules(schedules)
                .summary(IntakeResponseDTO.DailySummary.builder()
                        .totalScheduled(totalScheduled)
                        .totalTaken(totalTaken)
                        .completionRate(Math.round(completionRate * 10) / 10.0)
                        .build())
                .build();
    }

    /**
     * Reminder를 ScheduleMedication으로 변환 (약물/영양제 모두 지원)
     */
    private static IntakeResponseDTO.ScheduleMedication convertReminderToScheduleMedication(
            Reminder reminder,
            LocalDate date,
            MedicationTiming timing,
            Map<Long, List<Intake>> intakesByMedicationId,
            Map<Long, List<Intake>> intakesBySupplementId) {

        if (reminder.isMedicationReminder()) {
            return convertMedicationReminder(reminder, date, timing, intakesByMedicationId);
        } else {
            return convertSupplementReminder(reminder, date, timing, intakesBySupplementId);
        }
    }

    /**
     * 약물 리마인더 변환
     */
    private static IntakeResponseDTO.ScheduleMedication convertMedicationReminder(
            Reminder reminder,
            LocalDate date,
            MedicationTiming timing,
            Map<Long, List<Intake>> intakesByMedicationId) {

        UserMedication um = reminder.getUserMedication();
        List<Intake> medicationIntakes = intakesByMedicationId.getOrDefault(um.getId(), List.of());
        Intake todayIntake = findIntakeForDateAndTiming(medicationIntakes, date, timing);

        String displayName = null;
        String ingredientKr = null;
        String imageUrl = null;

        if (um.getDrugInfo() != null) {
            displayName = um.getDrugInfo().getDisplayName();
            ingredientKr = um.getDrugInfo().getIngredientKr();
            imageUrl = um.getDrugInfo().getImageUrl();
        }

        return IntakeResponseDTO.ScheduleMedication.builder()
                .id(um.getId())
                .name(um.getDrugName())
                .displayName(displayName)
                .ingredientKr(ingredientKr)
                .dosage(parseDosage(um.getDosage()))
                .taken(todayIntake != null)
                .takenAt(todayIntake != null ? todayIntake.getTakenAt() : null)
                .imageUrl(imageUrl)
                .isSupplement(false)
                .build();
    }

    /**
     * 영양제 리마인더 변환
     */
    private static IntakeResponseDTO.ScheduleMedication convertSupplementReminder(
            Reminder reminder,
            LocalDate date,
            MedicationTiming timing,
            Map<Long, List<Intake>> intakesBySupplementId) {

        UserSupplement us = reminder.getUserSupplement();
        Supplement supplement = us.getSupplement();
        List<Intake> supplementIntakes = intakesBySupplementId.getOrDefault(us.getId(), List.of());
        Intake todayIntake = findIntakeForDateAndTiming(supplementIntakes, date, timing);

        return IntakeResponseDTO.ScheduleMedication.builder()
                .id(us.getId())
                .name(supplement.getName())
                .displayName(supplement.getName())
                .ingredientKr(null)
                .dosage(parseDosage(us.getDosage()))
                .taken(todayIntake != null)
                .takenAt(todayIntake != null ? todayIntake.getTakenAt() : null)
                .imageUrl(supplement.getImageUrl())
                .isSupplement(true)
                .build();
    }

    private static Intake findIntakeForDateAndTiming(List<Intake> intakes, LocalDate date, MedicationTiming timing) {
        return intakes.stream()
                .filter(i -> i.getTakenAt().toLocalDate().equals(date) && i.getTiming() == timing)
                .findFirst()
                .orElse(null);
    }

    private static Integer parseDosage(String dosage) {
        String numStr = dosage.replaceAll("[^0-9]", "");
        return numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
    }
}
