package com.myyak.converter;

import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.web.dto.AdminDTO.AdminResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminConverter {

    // ============ 영양제 관련 변환 ============

    public static AdminResponseDTO.SupplementItem toSupplementItem(Supplement supplement) {
        return AdminResponseDTO.SupplementItem.builder()
                .id(supplement.getId())
                .name(supplement.getName())
                .tag(supplement.getTag())
                .tagDescription(supplement.getTag().getDescription())
                .selectionCount(supplement.getSelectionCount())
                .createdAt(supplement.getCreatedAt())
                .createdByName(supplement.getCreatedBy() != null ? supplement.getCreatedBy().getName() : "알 수 없음")
                .build();
    }

    public static AdminResponseDTO.SupplementList toSupplementList(Page<Supplement> supplementPage, int page, int size) {
        List<AdminResponseDTO.SupplementItem> items = supplementPage.getContent().stream()
                .map(AdminConverter::toSupplementItem)
                .collect(Collectors.toList());

        return AdminResponseDTO.SupplementList.builder()
                .supplements(items)
                .page(page)
                .size(size)
                .totalPages(supplementPage.getTotalPages())
                .totalElements(supplementPage.getTotalElements())
                .build();
    }

    public static AdminResponseDTO.SupplementTagStats toSupplementTagStats(Map<SupplementTag, Long> tagCounts, long totalCount) {
        return AdminResponseDTO.SupplementTagStats.builder()
                .tagCounts(tagCounts)
                .totalCount(totalCount)
                .build();
    }

    public static AdminResponseDTO.SupplementDeleteResult toSupplementDeleteResult(Long deletedSupplementId, int deletedUserSupplementCount) {
        return AdminResponseDTO.SupplementDeleteResult.builder()
                .deletedSupplementId(deletedSupplementId)
                .deletedUserSupplementCount(deletedUserSupplementCount)
                .build();
    }

    // ============ 사용자 통계 변환 ============

    public static AdminResponseDTO.UserStats toUserStats(long total, long today, long week, long month,
                                                         Map<String, Long> byGender,
                                                         Map<String, Long> byAgeGroup,
                                                         Map<String, Long> bySignupPurpose,
                                                         long reminderUsers, long pushReachableUsers) {
        return AdminResponseDTO.UserStats.builder()
                .total(total)
                .today(today)
                .week(week)
                .month(month)
                .byGender(byGender)
                .byAgeGroup(byAgeGroup)
                .bySignupPurpose(bySignupPurpose)
                .reminderUsers(reminderUsers)
                .pushReachableUsers(pushReachableUsers)
                .build();
    }

    public static AdminResponseDTO.DailyCount toDailyCount(LocalDate date, long count) {
        return AdminResponseDTO.DailyCount.builder()
                .date(date)
                .count(count)
                .build();
    }

    public static AdminResponseDTO.DailySignups toDailySignups(List<AdminResponseDTO.DailyCount> dailyCounts) {
        return AdminResponseDTO.DailySignups.builder()
                .dailyCounts(dailyCounts)
                .build();
    }

    // ============ 사용자 목록 변환 ============

    public static AdminResponseDTO.UserItem toUserItem(User user, int medicationCount, int supplementCount) {
        return AdminResponseDTO.UserItem.builder()
                .id(user.getId())
                .kakaoId(user.getKakaoId())
                .name(user.getName())
                .email(user.getEmail())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .ageRange(user.getAgeRange())
                .medicationCount(medicationCount)
                .supplementCount(supplementCount)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getUpdatedAt())
                .build();
    }

    public static AdminResponseDTO.UserList toUserList(Page<User> userPage,
                                                       Map<Long, Long> medicationCounts,
                                                       Map<Long, Long> supplementCounts,
                                                       int page, int size) {
        List<AdminResponseDTO.UserItem> items = userPage.getContent().stream()
                .map(user -> toUserItem(user,
                        medicationCounts.getOrDefault(user.getId(), 0L).intValue(),
                        supplementCounts.getOrDefault(user.getId(), 0L).intValue()))
                .collect(Collectors.toList());

        return AdminResponseDTO.UserList.builder()
                .users(items)
                .page(page)
                .size(size)
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .build();
    }

    public static AdminResponseDTO.BatchDeleteUsersResult toBatchDeleteUsersResult(int requestedCount,
                                                                                   List<Long> deletedUserIds,
                                                                                   List<Long> failedUserIds) {
        return AdminResponseDTO.BatchDeleteUsersResult.builder()
                .requestedCount(requestedCount)
                .deletedCount(deletedUserIds.size())
                .deletedUserIds(deletedUserIds)
                .failedUserIds(failedUserIds)
                .build();
    }
}
