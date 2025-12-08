package com.myyak.converter;

import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.ReminderDTO.ReminderResponseDTO;

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

    public static ReminderResponseDTO.ReminderItem toItem(Reminder reminder) {
        return ReminderResponseDTO.ReminderItem.builder()
                .id(reminder.getId())
                .medicationId(reminder.getUserMedication().getId())
                .medicationName(reminder.getUserMedication().getDrugName())
                .time(reminder.getTime().format(TIME_FORMATTER))
                .timing(reminder.getTiming())
                .enabled(reminder.getEnabled())
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
}
