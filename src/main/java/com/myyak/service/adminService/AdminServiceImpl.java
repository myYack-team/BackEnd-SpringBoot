package com.myyak.service.adminService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.domain.Supplement;
import com.myyak.domain.enums.Gender;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.repository.DrugInfoRepository;
import com.myyak.repository.SupplementRepository;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserSupplementRepository;
import com.myyak.web.dto.AdminDTO.AdminResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final DrugInfoRepository drugInfoRepository;
    private final SupplementRepository supplementRepository;
    private final UserRepository userRepository;
    private final UserSupplementRepository userSupplementRepository;

    @Override
    public AdminResponseDTO.DrugStats getDrugStats() {
        long totalCount = drugInfoRepository.count();
        long withoutEfficacy = drugInfoRepository.countByEfficacyIsNullOrEmpty();
        long withoutIngredient = drugInfoRepository.countByIngredientKrIsNull();

        return AdminResponseDTO.DrugStats.builder()
                .totalCount(totalCount)
                .withoutEfficacy(withoutEfficacy)
                .withoutIngredient(withoutIngredient)
                .build();
    }

    @Override
    public AdminResponseDTO.SupplementList getRecentSupplements(int page, int size, Integer days, String search) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Supplement> supplementPage;

        if (search != null && !search.isBlank()) {
            supplementPage = supplementRepository.searchByKeyword(search.trim(), pageRequest);
        } else {
            supplementPage = supplementRepository.findAll(pageRequest);
        }

        // days 필터링 (메모리에서 처리 - 단순화를 위해)
        LocalDateTime cutoffDate = days != null ? LocalDateTime.now().minusDays(days) : null;

        List<AdminResponseDTO.SupplementItem> items = supplementPage.getContent().stream()
                .filter(s -> cutoffDate == null || s.getCreatedAt().isAfter(cutoffDate))
                .map(this::toSupplementItem)
                .collect(Collectors.toList());

        return AdminResponseDTO.SupplementList.builder()
                .supplements(items)
                .page(page)
                .size(size)
                .totalPages(supplementPage.getTotalPages())
                .totalElements(supplementPage.getTotalElements())
                .build();
    }

    @Override
    public AdminResponseDTO.SupplementTagStats getSupplementTagStats() {
        List<Supplement> allSupplements = supplementRepository.findAll();

        Map<SupplementTag, Long> tagCounts = allSupplements.stream()
                .collect(Collectors.groupingBy(Supplement::getTag, Collectors.counting()));

        return AdminResponseDTO.SupplementTagStats.builder()
                .tagCounts(tagCounts)
                .totalCount(allSupplements.size())
                .build();
    }

    @Override
    public AdminResponseDTO.UserStats getUserStats() {
        var allUsers = userRepository.findAll();
        long total = allUsers.size();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minusDays(7);
        LocalDateTime monthStart = now.minusDays(30);

        long today = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(todayStart))
                .count();

        long week = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(weekStart))
                .count();

        long month = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(monthStart))
                .count();

        // 성별 분포
        Map<String, Long> byGender = new LinkedHashMap<>();
        byGender.put("MALE", allUsers.stream().filter(u -> u.getGender() == Gender.MALE).count());
        byGender.put("FEMALE", allUsers.stream().filter(u -> u.getGender() == Gender.FEMALE).count());
        byGender.put("UNKNOWN", allUsers.stream().filter(u -> u.getGender() == null).count());

        // 연령대 분포
        Map<String, Long> byAgeGroup = new LinkedHashMap<>();
        byAgeGroup.put("10s", countByAgeGroup(allUsers, 10, 19));
        byAgeGroup.put("20s", countByAgeGroup(allUsers, 20, 29));
        byAgeGroup.put("30s", countByAgeGroup(allUsers, 30, 39));
        byAgeGroup.put("40s", countByAgeGroup(allUsers, 40, 49));
        byAgeGroup.put("50s", countByAgeGroup(allUsers, 50, 59));
        byAgeGroup.put("60+", countByAgeGroup(allUsers, 60, 200));
        byAgeGroup.put("UNKNOWN", allUsers.stream().filter(u -> u.getBirthDate() == null).count());

        return AdminResponseDTO.UserStats.builder()
                .total(total)
                .today(today)
                .week(week)
                .month(month)
                .byGender(byGender)
                .byAgeGroup(byAgeGroup)
                .build();
    }

    @Override
    public AdminResponseDTO.DailySignups getDailySignups(int days) {
        var allUsers = userRepository.findAll();
        LocalDate today = LocalDate.now();

        List<AdminResponseDTO.DailyCount> dailyCounts = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            long count = allUsers.stream()
                    .filter(u -> u.getCreatedAt() != null &&
                            u.getCreatedAt().isAfter(startOfDay) &&
                            u.getCreatedAt().isBefore(endOfDay))
                    .count();

            dailyCounts.add(AdminResponseDTO.DailyCount.builder()
                    .date(date)
                    .count(count)
                    .build());
        }

        return AdminResponseDTO.DailySignups.builder()
                .dailyCounts(dailyCounts)
                .build();
    }

    @Override
    @Transactional
    public AdminResponseDTO.SupplementDeleteResult deleteSupplement(Long supplementId) {
        Supplement supplement = supplementRepository.findById(supplementId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND));

        // 해당 영양제를 선택한 UserSupplement 수 조회
        var userSupplements = userSupplementRepository.findAll().stream()
                .filter(us -> us.getSupplement().getId().equals(supplementId))
                .collect(Collectors.toList());

        int deletedUserSupplementCount = userSupplements.size();

        // UserSupplement 삭제
        userSupplementRepository.deleteAll(userSupplements);

        // Supplement 삭제
        supplementRepository.delete(supplement);

        log.info("영양제 삭제 완료: id={}, 삭제된 UserSupplement 수={}", supplementId, deletedUserSupplementCount);

        return AdminResponseDTO.SupplementDeleteResult.builder()
                .deletedSupplementId(supplementId)
                .deletedUserSupplementCount(deletedUserSupplementCount)
                .build();
    }

    private AdminResponseDTO.SupplementItem toSupplementItem(Supplement supplement) {
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

    private long countByAgeGroup(List<com.myyak.domain.User> users, int minAge, int maxAge) {
        LocalDate today = LocalDate.now();
        return users.stream()
                .filter(u -> u.getBirthDate() != null)
                .filter(u -> {
                    int age = Period.between(u.getBirthDate(), today).getYears();
                    return age >= minAge && age <= maxAge;
                })
                .count();
    }
}
