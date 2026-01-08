package com.myyak.util;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.service.storage.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 파일 업로드 유틸리티
 * StorageClient를 통해 로컬 또는 S3에 이미지를 저장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadUtil {

    private final StorageClient storageClient;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "webp"};

    /**
     * 처방전 이미지 업로드
     * @param file 업로드할 파일
     * @param userId 사용자 ID
     * @return 저장된 파일의 접근 URL
     */
    public String uploadPrescriptionImage(MultipartFile file, Long userId) {
        validateFile(file);

        String filename = generateFilename(getFileExtension(file.getOriginalFilename()));
        String path = String.format("prescriptions/%d", userId);

        log.info("[FileUpload] Provider: {}, Path: {}", storageClient.getProviderName(), path);
        return storageClient.upload(file, path, filename);
    }

    /**
     * 영양제 이미지 업로드
     * @param file 업로드할 파일
     * @return 저장된 파일의 접근 URL
     */
    public String uploadSupplementImage(MultipartFile file) {
        validateFile(file);

        String filename = generateFilename(getFileExtension(file.getOriginalFilename()));
        String path = "supplements";

        log.info("[FileUpload] Provider: {}, Path: {}", storageClient.getProviderName(), path);
        return storageClient.upload(file, path, filename);
    }

    /**
     * 파일 삭제 (동기)
     * @param fileUrl 파일 URL
     */
    public void deleteFile(String fileUrl) {
        storageClient.delete(fileUrl);
    }

    /**
     * 파일 삭제 (비동기)
     * DB 삭제 후 파일 삭제를 백그라운드에서 처리하여 응답 속도 개선
     * @param fileUrl 파일 URL
     */
    @Async
    public void deleteFileAsync(String fileUrl) {
        deleteFile(fileUrl);
    }

    /**
     * 여러 파일 삭제 (비동기)
     * @param fileUrls 파일 URL 목록
     */
    @Async
    public void deleteFilesAsync(List<String> fileUrls) {
        storageClient.deleteMultiple(fileUrls);
        log.info("Async file deletion completed: {} files", fileUrls != null ? fileUrls.size() : 0);
    }

    /**
     * 파일 유효성 검사
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        // 파일 크기 검사
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        // 확장자 검사
        String extension = getFileExtension(file.getOriginalFilename());
        boolean isAllowed = false;
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (allowedExt.equalsIgnoreCase(extension)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "png";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 유니크 파일명 생성
     */
    private String generateFilename(String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s.%s", timestamp, uuid, extension);
    }
}
