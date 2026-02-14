package com.myyak.service.supplementService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.SupplementConverter;
import com.myyak.domain.Reminder;
import com.myyak.domain.Supplement;
import com.myyak.domain.User;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.MedicationTiming;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.repository.ReminderRepository;
import com.myyak.repository.SupplementRepository;
import com.myyak.repository.UserSupplementRepository;
import com.myyak.service.supplementSearchService.SupplementSearchService;
import com.myyak.service.userService.UserService;
import com.myyak.util.FileUploadUtil;
import com.myyak.web.dto.SupplementDTO.SupplementRequestDTO;
import com.myyak.web.dto.SupplementDTO.SupplementResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplementServiceImpl implements SupplementService {

    private final SupplementRepository supplementRepository;
    private final UserSupplementRepository userSupplementRepository;
    private final ReminderRepository reminderRepository;
    private final UserService userService;
    private final SupplementSearchService supplementSearchService;
    private final FileUploadUtil fileUploadUtil;

    // ============ Supplement (마스터) ============

    @Override
    public Supplement findById(Long supplementId) {
        return supplementRepository.findById(supplementId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.SUPPLEMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public SupplementResponseDTO.CreateSupplementResult createSupplement(Long userId, SupplementRequestDTO.CreateSupplementRequest request) {
        User user = userService.findById(userId);
        Supplement supplement = SupplementConverter.toEntity(request, user);
        supplementRepository.save(supplement);

        // 캐시에 추가
        supplementSearchService.addOrUpdateCache(supplement);

        return SupplementConverter.toCreateResult(supplement);
    }

    @Override
    @Transactional
    public SupplementResponseDTO.CreateSupplementResult createSupplementWithImage(
            Long userId, String name, String description, String tagStr, MultipartFile image) {
        User user = userService.findById(userId);

        // 태그 문자열을 enum으로 변환
        SupplementTag tag;
        try {
            tag = SupplementTag.valueOf(tagStr);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(ErrorStatus.INVALID_SUPPLEMENT_TAG);
        }

        // 이미지 업로드
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            imageUrl = fileUploadUtil.uploadSupplementImage(image);
        }

        // 영양제 엔티티 생성
        Supplement supplement = Supplement.builder()
                .name(name)
                .description(description)
                .tag(tag)
                .imageUrl(imageUrl)
                .createdBy(user)
                .selectionCount(0)
                .build();

        supplementRepository.save(supplement);

        // 캐시에 추가
        supplementSearchService.addOrUpdateCache(supplement);

        return SupplementConverter.toCreateResult(supplement);
    }

    @Override
    public SupplementResponseDTO.SupplementList searchSupplements(String keyword, SupplementTag tag, int page, int size) {
        // 캐시 기반 검색 (DB 쿼리 없음)
        List<Supplement> supplements = supplementSearchService.searchFromCache(keyword, tag, page, size);
        long totalCount = supplementSearchService.countFromCache(keyword, tag);

        return SupplementConverter.toListFromCache(supplements, page, size, totalCount);
    }

    @Override
    public SupplementResponseDTO.SupplementList getPopularSupplements(int page, int size) {
        // 캐시 기반 인기 영양제 조회 (DB 쿼리 없음)
        List<Supplement> supplements = supplementSearchService.getPopularFromCache(page, size);
        long totalCount = supplementSearchService.countAll();

        return SupplementConverter.toListFromCache(supplements, page, size, totalCount);
    }

    @Override
    public SupplementResponseDTO.SupplementDetail getSupplementDetail(Long supplementId) {
        Supplement supplement = findById(supplementId);
        return SupplementConverter.toDetail(supplement);
    }

    @Override
    @Transactional
    public SupplementResponseDTO.SupplementDetail updateSupplement(Long userId, Long supplementId, SupplementRequestDTO.UpdateSupplementRequest request) {
        User user = userService.findById(userId);
        Supplement supplement = findById(supplementId);

        validateSupplementOwner(supplement, user);

        String name = request.getName() != null ? request.getName() : supplement.getName();
        String description = request.getDescription() != null ? request.getDescription() : supplement.getDescription();
        SupplementTag tag = request.getTag() != null ? request.getTag() : supplement.getTag();
        String imageUrl = request.getImageUrl() != null ? request.getImageUrl() : supplement.getImageUrl();

        supplement.update(name, description, tag, imageUrl);

        // 캐시 갱신
        supplementSearchService.addOrUpdateCache(supplement);

        return SupplementConverter.toDetail(supplement);
    }

    @Override
    @Transactional
    public void deleteSupplement(Long userId, Long supplementId) {
        User user = userService.findById(userId);
        Supplement supplement = findById(supplementId);

        validateSupplementOwner(supplement, user);

        supplementRepository.delete(supplement);

        // 캐시에서 제거
        supplementSearchService.removeFromCache(supplementId);
    }

    // ============ UserSupplement ============

    @Override
    public UserSupplement findUserSupplementById(Long userSupplementId) {
        return userSupplementRepository.findByIdAndIsActiveTrue(userSupplementId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_SUPPLEMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public SupplementResponseDTO.AddUserSupplementResult addUserSupplement(Long userId, SupplementRequestDTO.AddUserSupplementRequest request) {
        User user = userService.findById(userId);
        Supplement supplement = findById(request.getSupplementId());

        // 이미 등록된 영양제인지 확인
        userSupplementRepository.findByUserAndSupplementId(user, supplement.getId())
                .ifPresent(us -> {
                    throw new GeneralException(ErrorStatus.USER_SUPPLEMENT_ALREADY_EXISTS);
                });

        UserSupplement userSupplement = SupplementConverter.toUserSupplementEntity(request, user, supplement);
        userSupplementRepository.save(userSupplement);

        // 선택 횟수 증가
        supplement.incrementSelectionCount();

        // 캐시 갱신 (선택 횟수 변경됨)
        supplementSearchService.addOrUpdateCache(supplement);

        // 리마인더 생성
        List<MedicationTiming> timings = request.getTimings();
        for (MedicationTiming timing : timings) {
            if (timing != MedicationTiming.AS_NEEDED && timing.getDefaultTime() != null) {
                Reminder reminder = SupplementConverter.toReminderEntity(userSupplement, timing);
                reminderRepository.save(reminder);
            }
        }

        return SupplementConverter.toAddResult(userSupplement, timings);
    }

    @Override
    public SupplementResponseDTO.UserSupplementList getUserSupplements(Long userId) {
        User user = userService.findById(userId);
        List<UserSupplement> supplements = userSupplementRepository.findByUserWithSupplement(user);

        if (supplements.isEmpty()) {
            return SupplementConverter.toUserSupplementList(supplements, Map.of());
        }

        // N+1 방지: 모든 리마인더를 한 번에 조회 후 그룹핑
        List<Reminder> allReminders = reminderRepository.findByUserSupplementIn(supplements);
        Map<Long, List<MedicationTiming>> timingsMap = allReminders.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getUserSupplement().getId(),
                        Collectors.mapping(Reminder::getTiming, Collectors.toList())
                ));

        return SupplementConverter.toUserSupplementList(supplements, timingsMap);
    }

    @Override
    public SupplementResponseDTO.UserSupplementDetail getUserSupplementDetail(Long userId, Long userSupplementId) {
        User user = userService.findById(userId);
        UserSupplement userSupplement = findUserSupplementById(userSupplementId);

        validateUserSupplementOwner(userSupplement, user);

        List<Reminder> reminders = reminderRepository.findByUserSupplement(userSupplement);
        return SupplementConverter.toUserSupplementDetail(userSupplement, reminders);
    }

    @Override
    @Transactional
    public SupplementResponseDTO.UpdateUserSupplementResult updateUserSupplement(Long userId, Long userSupplementId, SupplementRequestDTO.UpdateUserSupplementRequest request) {
        User user = userService.findById(userId);
        UserSupplement userSupplement = findUserSupplementById(userSupplementId);

        validateUserSupplementOwner(userSupplement, user);

        String dosage = request.getDosage() != null ? request.getDosage() : userSupplement.getDosage();
        Integer frequency = request.getFrequency() != null ? request.getFrequency() : userSupplement.getFrequency();

        userSupplement.update(dosage, frequency, request.getEndDate(), request.getMemo());

        List<MedicationTiming> timings;
        if (request.getTimings() != null) {
            // 기존 리마인더 삭제 후 새로 생성
            reminderRepository.deleteByUserSupplement(userSupplement);

            for (MedicationTiming timing : request.getTimings()) {
                if (timing != MedicationTiming.AS_NEEDED && timing.getDefaultTime() != null) {
                    Reminder reminder = SupplementConverter.toReminderEntity(userSupplement, timing);
                    reminderRepository.save(reminder);
                }
            }
            timings = request.getTimings();
        } else {
            List<Reminder> reminders = reminderRepository.findByUserSupplement(userSupplement);
            timings = reminders.stream()
                    .map(Reminder::getTiming)
                    .collect(Collectors.toList());
        }

        return SupplementConverter.toUpdateResult(userSupplement, timings);
    }

    @Override
    @Transactional
    public void deleteUserSupplement(Long userId, Long userSupplementId) {
        User user = userService.findById(userId);
        UserSupplement userSupplement = findUserSupplementById(userSupplementId);

        validateUserSupplementOwner(userSupplement, user);

        // 연관 리마인더 비활성화
        List<Reminder> reminders = reminderRepository.findByUserSupplement(userSupplement);
        for (Reminder reminder : reminders) {
            reminder.disable();
        }

        // 선택 횟수 감소
        Supplement supplement = userSupplement.getSupplement();
        supplement.decrementSelectionCount();

        // 캐시 갱신 (선택 횟수 변경됨)
        supplementSearchService.addOrUpdateCache(supplement);

        userSupplement.deactivate();
    }

    @Override
    @Transactional
    public SupplementResponseDTO.BatchDeleteResult deleteUserSupplementsBatch(Long userId, List<Long> ids) {
        User user = userService.findById(userId);
        int requestedCount = ids.size();
        int deletedCount = 0;

        // 모든 UserSupplement 조회
        List<UserSupplement> userSupplements = userSupplementRepository.findAllById(ids);

        // 소유권 검증 (모든 항목이 현재 사용자의 것인지 확인)
        for (UserSupplement us : userSupplements) {
            if (!us.getUser().getId().equals(userId)) {
                throw new GeneralException(ErrorStatus.SUPPLEMENT_ACCESS_DENIED);
            }
        }

        // 삭제 처리
        for (UserSupplement userSupplement : userSupplements) {
            // 연관 리마인더 비활성화
            List<Reminder> reminders = reminderRepository.findByUserSupplement(userSupplement);
            for (Reminder reminder : reminders) {
                reminder.disable();
            }

            // selectionCount 감소
            Supplement supplement = userSupplement.getSupplement();
            supplement.decrementSelectionCount();

            // 캐시 갱신
            supplementSearchService.addOrUpdateCache(supplement);

            // 소프트 삭제
            userSupplement.deactivate();
            deletedCount++;
        }

        return SupplementResponseDTO.BatchDeleteResult.builder()
                .requestedCount(requestedCount)
                .deletedCount(deletedCount)
                .failedCount(requestedCount - deletedCount)
                .build();
    }

    // ============ Private Methods ============

    private void validateSupplementOwner(Supplement supplement, User user) {
        if (!supplement.getCreatedBy().getId().equals(user.getId())) {
            throw new GeneralException(ErrorStatus.SUPPLEMENT_ACCESS_DENIED);
        }
    }

    private void validateUserSupplementOwner(UserSupplement userSupplement, User user) {
        if (!userSupplement.getUser().getId().equals(user.getId())) {
            throw new GeneralException(ErrorStatus.SUPPLEMENT_ACCESS_DENIED);
        }
    }
}
