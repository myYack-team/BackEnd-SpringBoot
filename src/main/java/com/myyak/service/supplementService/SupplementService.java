package com.myyak.service.supplementService;

import com.myyak.domain.Supplement;
import com.myyak.domain.UserSupplement;
import com.myyak.domain.enums.SupplementTag;
import com.myyak.web.dto.SupplementDTO.SupplementRequestDTO;
import com.myyak.web.dto.SupplementDTO.SupplementResponseDTO;

public interface SupplementService {

    // ============ Supplement (마스터) ============

    Supplement findById(Long supplementId);

    SupplementResponseDTO.CreateSupplementResult createSupplement(Long userId, SupplementRequestDTO.CreateSupplementRequest request);

    SupplementResponseDTO.SupplementList searchSupplements(String keyword, SupplementTag tag, int page, int size);

    SupplementResponseDTO.SupplementList getPopularSupplements(int page, int size);

    SupplementResponseDTO.SupplementDetail getSupplementDetail(Long supplementId);

    SupplementResponseDTO.SupplementDetail updateSupplement(Long userId, Long supplementId, SupplementRequestDTO.UpdateSupplementRequest request);

    void deleteSupplement(Long userId, Long supplementId);

    // ============ UserSupplement ============

    UserSupplement findUserSupplementById(Long userSupplementId);

    SupplementResponseDTO.AddUserSupplementResult addUserSupplement(Long userId, SupplementRequestDTO.AddUserSupplementRequest request);

    SupplementResponseDTO.UserSupplementList getUserSupplements(Long userId);

    SupplementResponseDTO.UserSupplementDetail getUserSupplementDetail(Long userId, Long userSupplementId);

    SupplementResponseDTO.UpdateUserSupplementResult updateUserSupplement(Long userId, Long userSupplementId, SupplementRequestDTO.UpdateUserSupplementRequest request);

    void deleteUserSupplement(Long userId, Long userSupplementId);
}
