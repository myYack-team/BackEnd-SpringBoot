package com.myyak.service.medicationService;

import com.myyak.domain.UserMedication;
import com.myyak.web.dto.MedicationDTO.MedicationRequestDTO;
import com.myyak.web.dto.MedicationDTO.MedicationResponseDTO;

import java.util.List;

public interface MedicationService {

    UserMedication findById(Long userMedicationId);

    MedicationResponseDTO.CreateResult createMedication(Long userId, MedicationRequestDTO.CreateRequest request);

    MedicationResponseDTO.MedicationList getMedications(Long userId);

    MedicationResponseDTO.MedicationDetail getMedicationDetail(Long userId, Long medicationId);

    MedicationResponseDTO.UpdateResult updateMedication(Long userId, Long medicationId, MedicationRequestDTO.UpdateRequest request);

    void deleteMedication(Long userId, Long medicationId);

    MedicationResponseDTO.BatchDeleteResult deleteMedicationsBatch(Long userId, List<Long> ids);
}
