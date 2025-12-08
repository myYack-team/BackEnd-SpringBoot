package com.myyak.service.intakeService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.IntakeConverter;
import com.myyak.domain.Intake;
import com.myyak.domain.Reminder;
import com.myyak.domain.UserMedication;
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

        for (Long medicationId : request.getMedicationIds()) {
            UserMedication medication = userMedicationRepository.findById(medicationId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.MEDICATION_NOT_FOUND));

            if (!medication.getUser().getId().equals(userId)) {
                throw new GeneralException(ErrorStatus.MEDICATION_ACCESS_DENIED);
            }

            Intake intake = IntakeConverter.toEntity(medication, timing, request.getTakenAt());
            intakeRepository.save(intake);
            intakes.add(intake);

            String dosageStr = medication.getDosage().replaceAll("[^0-9]", "");
            int dosage = dosageStr.isEmpty() ? 1 : Integer.parseInt(dosageStr);
            medication.decreaseRemainingCount(dosage);
            medications.add(medication);
        }

        return IntakeConverter.toCreateResult(intakes, medications);
    }

    @Override
    public IntakeResponseDTO.DailyIntakeResult getIntakes(Long userId, LocalDate date) {
        userService.findById(userId);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Reminder> reminders = reminderRepository.findEnabledByUserId(userId);
        List<Intake> intakes = intakeRepository.findByUserIdAndDateRange(userId, startOfDay, endOfDay);

        Map<Long, List<Intake>> intakesByMedicationId = intakes.stream()
                .collect(Collectors.groupingBy(i -> i.getUserMedication().getId()));

        return IntakeConverter.toDailyResult(date, reminders, intakesByMedicationId);
    }
}
