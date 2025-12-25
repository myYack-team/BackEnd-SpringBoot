package com.myyak.converter;

import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.ReminderDTO.ReminderResponseDTO;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ReminderConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static Reminder toEntity(UserMedication userMedication, MedicationTiming timing) {
        return Reminder.builder()
                .userMedication(userMedication)
                .timing(timing)
                .time(timing.getDefaultTime())
                .enabled(true)
                .build();
    }

    /**
     * 커스텀 시간을 사용하여 Reminder 생성
     */
    public static Reminder toEntity(UserMedication userMedication, MedicationTiming timing, LocalTime customTime) {
        return Reminder.builder()
                .userMedication(userMedication)
                .timing(timing)
                .time(customTime)
                .enabled(true)
                .build();
    }

    public static ReminderResponseDTO.ReminderItem toItem(Reminder reminder) {
        Long medicationId = null;
        Long supplementId = null;
        String name = null;

        if (reminder.getUserMedication() != null) {
            medicationId = reminder.getUserMedication().getId();
            name = reminder.getUserMedication().getDrugName();
        } else if (reminder.getUserSupplement() != null) {
            supplementId = reminder.getUserSupplement().getId();
            name = reminder.getUserSupplement().getSupplementName();
        }

        return ReminderResponseDTO.ReminderItem.builder()
                .id(reminder.getId())
                .medicationId(medicationId)
                .supplementId(supplementId)
                .medicationName(name)
                .time(reminder.getTime().format(TIME_FORMATTER))
                .timing(reminder.getTiming())
                .enabled(reminder.getEnabled())
                .snoozeUntil(reminder.getSnoozeUntil())
                .build();
    }

    public static ReminderResponseDTO.ReminderList toList(List<Reminder> reminders) {
        List<ReminderResponseDTO.ReminderItem> items = reminders.stream()
                .map(ReminderConverter::toItem)
                .collect(Collectors.toList());

        return ReminderResponseDTO.ReminderList.builder()
                .reminders(items)
                .totalCount(items.size())
                .build();
    }

    public static ReminderResponseDTO.UpdateResult toUpdateResult(Reminder reminder) {
        return ReminderResponseDTO.UpdateResult.builder()
                .id(reminder.getId())
                .time(reminder.getTime().format(TIME_FORMATTER))
                .enabled(reminder.getEnabled())
                .build();
    }

    public static ReminderResponseDTO.ToggleResult toToggleResult(Reminder reminder) {
        return ReminderResponseDTO.ToggleResult.builder()
                .id(reminder.getId())
                .enabled(reminder.getEnabled())
                .build();
    }

    public static ReminderResponseDTO.SnoozeResult toSnoozeResult(Reminder reminder, int minutes) {
        return ReminderResponseDTO.SnoozeResult.builder()
                .id(reminder.getId())
                .snoozeUntil(reminder.getSnoozeUntil())
                .snoozeMinutes(minutes)
                .build();
    }

    public static ReminderResponseDTO.SnoozeResult toClearSnoozeResult(Reminder reminder) {
        return ReminderResponseDTO.SnoozeResult.builder()
                .id(reminder.getId())
                .snoozeUntil(null)
                .snoozeMinutes(0)
                .build();
    }
}
