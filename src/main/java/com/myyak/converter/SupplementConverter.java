package com.myyak.converter;

import com.myyak.domain.Reminder;
import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.web.dto.SupplementDTO.SupplementRequestDTO;
import com.myyak.web.dto.SupplementDTO.SupplementResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SupplementConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // ============ Supplement (마스터) 변환 ============

    public static Supplement toEntity(SupplementRequestDTO.CreateSupplementRequest request, User user) {
        return Supplement.builder()
                .name(request.getName())
                .description(request.getDescription())
                .tag(request.getTag())
                .imageUrl(request.getImageUrl())
                .createdBy(user)
                .selectionCount(0)
                .build();
    }

    public static SupplementResponseDTO.CreateSupplementResult toCreateResult(Supplement supplement) {
        return SupplementResponseDTO.CreateSupplementResult.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .tag(supplement.getTag())
                .tagLabel(supplement.getTag().getDescription())
                .createdAt(supplement.getCreatedAt())
                .build();
    }

    public static SupplementResponseDTO.SupplementListItem toListItem(Supplement supplement) {
        return SupplementResponseDTO.SupplementListItem.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .description(supplement.getDescription())
                .tag(supplement.getTag())
                .tagLabel(supplement.getTag().getDescription())
                .imageUrl(supplement.getImageUrl())
                .selectionCount(supplement.getSelectionCount())
                .createdByName(supplement.getCreatedBy().getName())
                .createdById(supplement.getCreatedBy().getId())
                .build();
    }

    public static SupplementResponseDTO.SupplementList toList(Page<Supplement> supplementPage) {
        List<SupplementResponseDTO.SupplementListItem> items = supplementPage.getContent().stream()
                .map(SupplementConverter::toListItem)
                .collect(Collectors.toList());

        return SupplementResponseDTO.SupplementList.builder()
                .supplements(items)
                .totalCount((int) supplementPage.getTotalElements())
                .page(supplementPage.getNumber())
                .size(supplementPage.getSize())
                .totalPages(supplementPage.getTotalPages())
                .hasNext(supplementPage.hasNext())
                .build();
    }

    /**
     * 캐시 기반 검색 결과를 SupplementList로 변환
     */
    public static SupplementResponseDTO.SupplementList toListFromCache(List<Supplement> supplements, int page, int size, long totalCount) {
        List<SupplementResponseDTO.SupplementListItem> items = supplements.stream()
                .map(SupplementConverter::toListItem)
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) totalCount / size);
        boolean hasNext = (long) (page + 1) * size < totalCount;

        return SupplementResponseDTO.SupplementList.builder()
                .supplements(items)
                .totalCount((int) totalCount)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .hasNext(hasNext)
                .build();
    }

    public static SupplementResponseDTO.SupplementDetail toDetail(Supplement supplement) {
        return SupplementResponseDTO.SupplementDetail.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .description(supplement.getDescription())
                .tag(supplement.getTag())
                .tagLabel(supplement.getTag().getDescription())
                .imageUrl(supplement.getImageUrl())
                .selectionCount(supplement.getSelectionCount())
                .createdByName(supplement.getCreatedBy().getName())
                .createdById(supplement.getCreatedBy().getId())
                .createdAt(supplement.getCreatedAt())
                .build();
    }

    // ============ UserSupplement 변환 ============

    public static UserSupplement toUserSupplementEntity(SupplementRequestDTO.AddUserSupplementRequest request,
                                                          User user, Supplement supplement) {
        return UserSupplement.builder()
                .user(user)
                .supplement(supplement)
                .dosage(request.getDosage())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .memo(request.getMemo())
                .isActive(true)
                .build();
    }

    public static SupplementResponseDTO.AddUserSupplementResult toAddResult(UserSupplement userSupplement,
                                                                               List<MedicationTiming> timings) {
        return SupplementResponseDTO.AddUserSupplementResult.builder()
                .id(userSupplement.getId())
                .supplementName(userSupplement.getSupplementName())
                .dosage(userSupplement.getDosage())
                .frequency(userSupplement.getFrequency())
                .timings(timings)
                .createdAt(userSupplement.getCreatedAt())
                .build();
    }

    public static SupplementResponseDTO.UserSupplementListItem toUserSupplementListItem(UserSupplement userSupplement,
                                                                                           List<MedicationTiming> timings) {
        Supplement supplement = userSupplement.getSupplement();
        return SupplementResponseDTO.UserSupplementListItem.builder()
                .id(userSupplement.getId())
                .supplementId(supplement.getId())
                .supplementName(supplement.getName())
                .tag(supplement.getTag())
                .tagLabel(supplement.getTag().getDescription())
                .imageUrl(supplement.getImageUrl())
                .dosage(userSupplement.getDosage())
                .frequency(userSupplement.getFrequency())
                .timings(timings)
                .build();
    }

    public static SupplementResponseDTO.UserSupplementList toUserSupplementList(List<UserSupplement> userSupplements,
                                                                                   Map<Long, List<MedicationTiming>> timingsMap) {
        List<SupplementResponseDTO.UserSupplementListItem> items = userSupplements.stream()
                .map(us -> toUserSupplementListItem(us, timingsMap.getOrDefault(us.getId(), List.of())))
                .collect(Collectors.toList());

        return SupplementResponseDTO.UserSupplementList.builder()
                .supplements(items)
                .totalCount(items.size())
                .build();
    }

    public static SupplementResponseDTO.UserSupplementDetail toUserSupplementDetail(UserSupplement userSupplement,
                                                                                       List<Reminder> reminders) {
        Supplement supplement = userSupplement.getSupplement();

        List<SupplementResponseDTO.ReminderInfo> reminderInfos = reminders.stream()
                .map(r -> SupplementResponseDTO.ReminderInfo.builder()
                        .id(r.getId())
                        .time(r.getTime().format(TIME_FORMATTER))
                        .timing(r.getTiming())
                        .timingLabel(r.getTiming().getDescription())
                        .enabled(r.getEnabled())
                        .build())
                .collect(Collectors.toList());

        List<MedicationTiming> timings = reminders.stream()
                .map(Reminder::getTiming)
                .collect(Collectors.toList());

        SupplementResponseDTO.SupplementInfo supplementInfo = SupplementResponseDTO.SupplementInfo.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .description(supplement.getDescription())
                .tag(supplement.getTag())
                .tagLabel(supplement.getTag().getDescription())
                .imageUrl(supplement.getImageUrl())
                .selectionCount(supplement.getSelectionCount())
                .createdByName(supplement.getCreatedBy().getName())
                .build();

        return SupplementResponseDTO.UserSupplementDetail.builder()
                .id(userSupplement.getId())
                .dosage(userSupplement.getDosage())
                .frequency(userSupplement.getFrequency())
                .timings(timings)
                .startDate(userSupplement.getStartDate())
                .endDate(userSupplement.getEndDate())
                .memo(userSupplement.getMemo())
                .createdAt(userSupplement.getCreatedAt())
                .reminders(reminderInfos)
                .supplementInfo(supplementInfo)
                .build();
    }

    public static SupplementResponseDTO.UpdateUserSupplementResult toUpdateResult(UserSupplement userSupplement,
                                                                                     List<MedicationTiming> timings) {
        return SupplementResponseDTO.UpdateUserSupplementResult.builder()
                .id(userSupplement.getId())
                .supplementName(userSupplement.getSupplementName())
                .dosage(userSupplement.getDosage())
                .frequency(userSupplement.getFrequency())
                .timings(timings)
                .build();
    }

    // ============ Reminder for Supplement ============

    public static Reminder toReminderEntity(UserSupplement userSupplement, MedicationTiming timing) {
        return Reminder.builder()
                .userSupplement(userSupplement)
                .timing(timing)
                .time(timing.getDefaultTime())
                .enabled(true)
                .build();
    }

    /**
     * 커스텀 시간을 사용하여 영양제 Reminder 생성
     */
    public static Reminder toReminderEntity(UserSupplement userSupplement, MedicationTiming timing, LocalTime customTime) {
        return Reminder.builder()
                .userSupplement(userSupplement)
                .timing(timing)
                .time(customTime)
                .enabled(true)
                .build();
    }
}
