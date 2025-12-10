package com.myyak.util;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 파일 업로드 유틸리티
 * 로컬 파일 시스템에 이미지를 저장하고 URL을 반환
 */
@Slf4j
@Component
public class FileUploadUtil {

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload.base-url:http://localhost:8080}")
    private String baseUrl;

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

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String newFilename = generateFilename(extension);

        // 경로: uploads/prescriptions/{userId}/{filename}
        String subPath = String.format("prescriptions/%d", userId);
        Path targetDir = Paths.get(uploadPath, subPath);

        try {
            // 디렉토리 생성
            Files.createDirectories(targetDir);

            // 파일 저장
            Path targetPath = targetDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File uploaded: {}", targetPath);

            // URL 반환 (상대 경로)
            return String.format("/uploads/%s/%s", subPath, newFilename);

        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 파일 삭제
     * @param fileUrl 파일 URL (상대 경로)
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            // URL에서 경로 추출 (/uploads/prescriptions/1/xxx.png -> uploads/prescriptions/1/xxx.png)
            String relativePath = fileUrl.startsWith("/uploads/")
                    ? fileUrl.substring("/uploads/".length())
                    : fileUrl;

            Path filePath = Paths.get(uploadPath).resolve(relativePath.replace("/uploads/", ""));

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted: {}", filePath);
            }

        } catch (IOException e) {
            log.warn("Failed to delete file: {}", e.getMessage());
        }
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
