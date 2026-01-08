package com.myyak.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 로컬 파일시스템 저장소 어댑터
 * storage.provider=local 이거나 설정이 없을 때 기본값으로 활성화됩니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageAdapter implements StorageClient {

    private final String uploadPath;

    public LocalStorageAdapter(@Value("${file.upload.path:uploads}") String uploadPath) {
        this.uploadPath = uploadPath;
        log.info("[LocalStorageAdapter] 초기화 완료 - Path: {}", uploadPath);
    }

    @Override
    public String upload(MultipartFile file, String path, String filename) {
        Path targetDir = Paths.get(uploadPath, path);

        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("[Local] 파일 업로드 완료: {}", targetPath);
            return String.format("/uploads/%s/%s", path, filename);

        } catch (IOException e) {
            log.error("[Local] 파일 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("로컬 업로드 실패", e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            String relativePath = fileUrl.startsWith("/uploads/")
                    ? fileUrl.substring("/uploads/".length())
                    : fileUrl;

            Path filePath = Paths.get(uploadPath, relativePath);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("[Local] 파일 삭제 완료: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("[Local] 파일 삭제 실패: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMultiple(List<String> fileUrls) {
        if (fileUrls == null) {
            return;
        }
        fileUrls.forEach(this::delete);
        log.info("[Local] {} 개 파일 삭제 완료", fileUrls.size());
    }

    @Override
    public String getProviderName() {
        return "Local FileSystem";
    }
}
