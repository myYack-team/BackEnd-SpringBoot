package com.myyak.service.reminderService;

import com.myyak.web.dto.ReminderDTO.ReminderRequestDTO;
import com.myyak.web.dto.ReminderDTO.ReminderResponseDTO;

public interface ReminderService {

    ReminderResponseDTO.ReminderList getReminders(Long userId);

    ReminderResponseDTO.UpdateResult updateReminderTime(Long userId, Long reminderId, ReminderRequestDTO.UpdateTimeRequest request);

    ReminderResponseDTO.ToggleResult toggleReminder(Long userId, Long reminderId);

    ReminderResponseDTO.SnoozeResult snoozeReminder(Long userId, Long reminderId, ReminderRequestDTO.SnoozeRequest request);

    ReminderResponseDTO.SnoozeResult clearSnooze(Long userId, Long reminderId);
}
