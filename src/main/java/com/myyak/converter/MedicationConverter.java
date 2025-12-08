package com.myyak.converter;

import com.myyak.domain.DrugInfo;
import com.myyak.domain.Reminder;
import com.myyak.domain.User;
import com.myyak.domain.UserMedication;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.MedicationDTO.MedicationRequestDTO;
import com.myyak.web.dto.MedicationDTO.MedicationResponseDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class MedicationConverter {

    public static UserMedication toEntity(MedicationRequestDTO.CreateRequest request, User user, DrugInfo drugInfo) {
        return UserMedication.builder()
                .user(user)
                .drugInfo(drugInfo)
                .customDrugName(drugInfo == null ? request.getCustomDrugName() : null)
                .dosage(request.getDosage() + "정")
                .frequency(request.getFrequency())
                .durationDays(request.getDurationDays())
                .totalCount(request.getTotalCount())
                .remainingCount(request.getTotalCount())
                .startDate(request.getStartDate())
                .endDate(request.getStartDate().plusDays(request.getDurationDays()))
                .isActive(true)
                .memo(request.getMemo())
                .build();
    }

    public static MedicationResponseDTO.CreateResult toCreateResult(UserMedication medication, List<MedicationTiming> timings) {
        return MedicationResponseDTO.CreateResult.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .dosage(parseDosage(medication.getDosage()))
                .frequency(medication.getFrequency())
                .timings(timings)
                .durationDays(medication.getDurationDays())
                .totalCount(medication.getTotalCount())
                .remainingCount(medication.getRemainingCount())
                .startDate(medication.getStartDate())
                .createdAt(medication.getCreatedAt())
                .build();
    }

    public static MedicationResponseDTO.MedicationListItem toListItem(UserMedication medication, List<MedicationTiming> timings) {
        int daysLeft = calculateDaysLeft(medication);
        String imageUrl = medication.getDrugInfo() != null ? medication.getDrugInfo().getImageUrl() : null;

        return MedicationResponseDTO.MedicationListItem.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .imageUrl(imageUrl)
                .dosage(parseDosage(medication.getDosage()))
                .frequency(medication.getFrequency())
                .timings(timings)
                .remainingCount(medication.getRemainingCount())
                .daysLeft(daysLeft)
                .build();
    }

    public static MedicationResponseDTO.MedicationList toList(List<UserMedication> medications,
                                                               java.util.Map<Long, List<MedicationTiming>> timingsMap) {
        List<MedicationResponseDTO.MedicationListItem> items = medications.stream()
                .map(m -> toListItem(m, timingsMap.getOrDefault(m.getId(), List.of())))
                .collect(Collectors.toList());

        return MedicationResponseDTO.MedicationList.builder()
                .medications(items)
                .totalCount(items.size())
                .build();
    }

    public static MedicationResponseDTO.MedicationDetail toDetail(UserMedication medication, List<Reminder> reminders) {
        List<MedicationResponseDTO.ReminderInfo> reminderInfos = reminders.stream()
                .map(r -> MedicationResponseDTO.ReminderInfo.builder()
                        .id(r.getId())
                        .time(r.getTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                        .timing(r.getTiming())
                        .timingLabel(r.getTiming().getDescription())
                        .enabled(r.getEnabled())
                        .build())
                .collect(Collectors.toList());

        List<MedicationTiming> timings = reminders.stream()
                .map(Reminder::getTiming)
                .collect(Collectors.toList());

        // DrugInfo 데이터 변환
        MedicationResponseDTO.DrugInfoData drugInfoData = null;
        if (medication.getDrugInfo() != null) {
            DrugInfo di = medication.getDrugInfo();
            drugInfoData = MedicationResponseDTO.DrugInfoData.builder()
                    .itemSeq(di.getItemSeq())
                    .itemName(di.getItemName())
                    .entpName(di.getEntpName())
                    .efficacy(di.getEfficacy())
                    .useMethod(di.getUseMethod())
                    .warning(di.getWarning())
                    .caution(di.getCaution())
                    .interaction(di.getInteraction())
                    .sideEffect(di.getSideEffect())
                    .storageMethod(di.getStorageMethod())
                    .imageUrl(di.getImageUrl())
                    .build();
        }

        return MedicationResponseDTO.MedicationDetail.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .dosage(parseDosage(medication.getDosage()))
                .frequency(medication.getFrequency())
                .timings(timings)
                .durationDays(medication.getDurationDays())
                .totalCount(medication.getTotalCount())
                .remainingCount(medication.getRemainingCount())
                .startDate(medication.getStartDate())
                .createdAt(medication.getCreatedAt())
                .memo(medication.getMemo())
                .reminders(reminderInfos)
                .drugInfo(drugInfoData)
                .build();
    }

    public static MedicationResponseDTO.UpdateResult toUpdateResult(UserMedication medication, List<MedicationTiming> timings) {
        return MedicationResponseDTO.UpdateResult.builder()
                .id(medication.getId())
                .drugName(medication.getDrugName())
                .dosage(parseDosage(medication.getDosage()))
                .frequency(medication.getFrequency())
                .timings(timings)
                .build();
    }

    private static Integer parseDosage(String dosage) {
        String numStr = dosage.replaceAll("[^0-9]", "");
        return numStr.isEmpty() ? 1 : Integer.parseInt(numStr);
    }

    private static int calculateDaysLeft(UserMedication medication) {
        if (medication.getEndDate() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), medication.getEndDate());
        return Math.max(0, (int) days);
    }
}
