package com.myyak.converter;

import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
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

    public static TodayResponseDTO.TodayResult toResult(
            LocalDate date,
            List<Reminder> reminders,
            Map<Long, List<Intake>> intakesByMedicationId) {

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
                    .map(r -> {
                        UserMedication um = r.getUserMedication();
                        List<Intake> medicationIntakes = intakesByMedicationId.getOrDefault(um.getId(), List.of());
                        boolean taken = medicationIntakes.stream()
                                .anyMatch(i -> i.getTakenAt().toLocalDate().equals(date));

                        return TodayResponseDTO.TodayMedication.builder()
                                .id(um.getId())
                                .name(um.getDrugName())
                                .dosage(parseDosage(um.getDosage()))
                                .taken(taken)
                                .build();
                    })
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

    private static Integer parseDosage(String dosage) {
        String numStr = dosage.replaceAll("[^0-9]", "");
        return numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
    }
}
