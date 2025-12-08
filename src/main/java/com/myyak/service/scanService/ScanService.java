package com.myyak.service.scanService;

import com.myyak.web.dto.ScanDTO.ScanResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ScanService {

    ScanResponseDTO.ScanResult scanPrescription(MultipartFile image);
}
