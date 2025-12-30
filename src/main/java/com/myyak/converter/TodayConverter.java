package com.myyak.converter;

import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.Supplement;
import com.myyak.domain.UserMedication;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.DrugType;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.web.dto.TodayDTO.TodayResponseDTO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TodayConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Map<DayOfWeek, String> DAY_OF_WEEK_KR = Map.of(
            DayOfWeek.MONDAY, "월",
            DayOfWeek.TUESDAY, "화",
            DayOfWeek.WEDNESDAY, "수",
            DayOfWeek.THURSDAY, "목",
            DayOfWeek.FRIDAY, "금",
            DayOfWeek.SATURDAY, "토",
            DayOfWeek.SUNDAY, "일"
    );

    /**
     * 오늘의 복약 결과 변환 (약물 + 영양제 통합)
     */
    public static TodayResponseDTO.TodayResult toResult(
            LocalDate date,
            List<Reminder> reminders,
            Map<Long, List<Intake>> intakesByMedicationId,
            Map<Long, List<Intake>> intakesBySupplementId) {

        Map<MedicationTiming, List<Reminder>> remindersByTiming = reminders.stream()
                .collect(Collectors.groupingBy(Reminder::getTiming));

        List<TodayResponseDTO.TodaySchedule> schedules = new ArrayList<>();
        int totalMedications = 0;
        int takenCount = 0;

        for (MedicationTiming timing : MedicationTiming.values()) {
            if (timing == MedicationTiming.AS_NEEDED) continue;

            List<Reminder> timingReminders = remindersByTiming.getOrDefault(timing, List.of());
            if (timingReminders.isEmpty()) continue;

            List<TodayResponseDTO.TodayMedication> meds = timingReminders.stream()
                    .map(r -> convertReminderToMedication(r, date, timing, intakesByMedicationId, intakesBySupplementId))
                    .collect(Collectors.toList());

            totalMedications += meds.size();
            takenCount += (int) meds.stream().filter(TodayResponseDTO.TodayMedication::getTaken).count();

            boolean allTaken = meds.stream().allMatch(TodayResponseDTO.TodayMedication::getTaken);
            String scheduledTime = timingReminders.get(0).getTime().format(TIME_FORMATTER);

            schedules.add(TodayResponseDTO.TodaySchedule.builder()
                    .timing(timing)
                    .timingLabel(timing.getDescription())
                    .scheduledTime(scheduledTime)
                    .medications(meds)
                    .allTaken(allTaken)
                    .build());
        }

        return TodayResponseDTO.TodayResult.builder()
                .date(date.format(DATE_FORMATTER))
                .dayOfWeek(DAY_OF_WEEK_KR.get(date.getDayOfWeek()))
                .schedules(schedules)
                .summary(TodayResponseDTO.TodaySummary.builder()
                        .totalMedications(totalMedications)
                        .takenCount(takenCount)
                        .remainingCount(totalMedications - takenCount)
                        .build())
                .build();
    }

    /**
     * Reminder를 TodayMedication으로 변환 (약물/영양제 모두 지원)
     */
    private static TodayResponseDTO.TodayMedication convertReminderToMedication(
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
    private static TodayResponseDTO.TodayMedication convertMedicationReminder(
            Reminder reminder,
            LocalDate date,
            MedicationTiming timing,
            Map<Long, List<Intake>> intakesByMedicationId) {

        UserMedication um = reminder.getUserMedication();
        List<Intake> medicationIntakes = intakesByMedicationId.getOrDefault(um.getId(), List.of());

        boolean taken = medicationIntakes.stream()
                .anyMatch(i -> i.getTakenAt().toLocalDate().equals(date) && i.getTiming() == timing);

        DrugType drugType = DrugType.UNKNOWN;
        String imageUrl = null;
        String displayName = um.getCustomDrugName();

        if (um.getDrugInfo() != null) {
            drugType = um.getDrugInfo().getDrugType() != null
                    ? um.getDrugInfo().getDrugType()
                    : DrugType.UNKNOWN;
            imageUrl = um.getDrugInfo().getImageUrl();
            displayName = um.getDrugInfo().getDisplayName() != null
                    ? um.getDrugInfo().getDisplayName()
                    : um.getDrugInfo().getItemName();
        }

        return TodayResponseDTO.TodayMedication.builder()
                .id(um.getId())
                .name(um.getDrugName())
                .displayName(displayName)
                .dosage(parseDosage(um.getDosage()))
                .taken(taken)
                .reminderId(reminder.getId())
                .drugType(drugType)
                .supplementTag(null)
                .isSupplement(false)
                .imageUrl(imageUrl)
                .build();
    }

    /**
     * 영양제 리마인더 변환
     */
    private static TodayResponseDTO.TodayMedication convertSupplementReminder(
            Reminder reminder,
            LocalDate date,
            MedicationTiming timing,
            Map<Long, List<Intake>> intakesBySupplementId) {

        UserSupplement us = reminder.getUserSupplement();
        Supplement supplement = us.getSupplement();
        List<Intake> supplementIntakes = intakesBySupplementId.getOrDefault(us.getId(), List.of());

        boolean taken = supplementIntakes.stream()
                .anyMatch(i -> i.getTakenAt().toLocalDate().equals(date) && i.getTiming() == timing);

        SupplementTag supplementTag = supplement.getTag();
        String imageUrl = supplement.getImageUrl();
        String displayName = supplement.getName();

        return TodayResponseDTO.TodayMedication.builder()
                .id(us.getId())
                .name(supplement.getName())
                .displayName(displayName)
                .dosage(parseDosage(us.getDosage()))
                .taken(taken)
                .reminderId(reminder.getId())
                .drugType(null)
                .supplementTag(supplementTag)
                .isSupplement(true)
                .imageUrl(imageUrl)
                .build();
    }

    private static Integer parseDosage(String dosage) {
        String numStr = dosage.replaceAll("[^0-9]", "");
        return numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
    }
}
