package com.myyak.service.reminderService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.ReminderConverter;
import com.myyak.domain.Reminder;
import com.myyak.repository.ReminderRepository;
import com.myyak.service.userService.UserService;
import com.myyak.web.dto.ReminderDTO.ReminderRequestDTO;
import com.myyak.web.dto.ReminderDTO.ReminderResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderServiceImpl implements ReminderService {

    private final ReminderRepository reminderRepository;
    private final UserService userService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public ReminderResponseDTO.ReminderList getReminders(Long userId) {
        userService.findById(userId);
        List<Reminder> reminders = reminderRepository.findByUserId(userId);
        return ReminderConverter.toList(reminders);
    }

    @Override
    @Transactional
    public ReminderResponseDTO.UpdateResult updateReminderTime(Long userId, Long reminderId, ReminderRequestDTO.UpdateTimeRequest request) {
        userService.findById(userId);
        Reminder reminder = findByIdAndValidateOwner(reminderId, userId);

        LocalTime time = LocalTime.parse(request.getTime(), TIME_FORMATTER);
        reminder.updateTime(time);

        return ReminderConverter.toUpdateResult(reminder);
    }

    @Override
    @Transactional
    public ReminderResponseDTO.ToggleResult toggleReminder(Long userId, Long reminderId) {
        userService.findById(userId);
        Reminder reminder = findByIdAndValidateOwner(reminderId, userId);

        reminder.toggle();

        return ReminderConverter.toToggleResult(reminder);
    }

    @Override
    @Transactional
    public ReminderResponseDTO.SnoozeResult snoozeReminder(Long userId, Long reminderId, ReminderRequestDTO.SnoozeRequest request) {
        userService.findById(userId);
        Reminder reminder = findByIdAndValidateOwner(reminderId, userId);

        int minutes = request.getMinutes();
        reminder.snooze(minutes);

        return ReminderConverter.toSnoozeResult(reminder, minutes);
    }

    @Override
    @Transactional
    public ReminderResponseDTO.SnoozeResult clearSnooze(Long userId, Long reminderId) {
        userService.findById(userId);
        Reminder reminder = findByIdAndValidateOwner(reminderId, userId);

        reminder.clearSnooze();

        return ReminderConverter.toClearSnoozeResult(reminder);
    }

    private Reminder findByIdAndValidateOwner(Long reminderId, Long userId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REMINDER_NOT_FOUND));

        // 의약품 알림인 경우
        if (reminder.getUserMedication() != null) {
            if (!reminder.getUserMedication().getUser().getId().equals(userId)) {
                throw new GeneralException(ErrorStatus._FORBIDDEN);
            }
        }
        // 영양제 알림인 경우
        else if (reminder.getUserSupplement() != null) {
            if (!reminder.getUserSupplement().getUser().getId().equals(userId)) {
                throw new GeneralException(ErrorStatus._FORBIDDEN);
            }
        }
        else {
            throw new GeneralException(ErrorStatus.REMINDER_NOT_FOUND);
        }

        return reminder;
    }
}
