package com.myyak.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;

/**
 * AWS S3 저장소 어댑터
 * storage.provider=s3 일 때 활성화됩니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3StorageAdapter implements StorageClient {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3StorageAdapter(
            S3Client s3Client,
            @Value("${storage.s3.bucket-name}") String bucketName,
            @Value("${storage.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
        log.info("[S3StorageAdapter] 초기화 완료 - Bucket: {}, Region: {}", bucketName, region);
    }

    @Override
    public String upload(MultipartFile file, String path, String filename) {
        String key = path + "/" + filename;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));

            String url = String.format("https://%s.s3.%s.amazonaws.com/%s",
                    bucketName, region, key);

            log.info("[S3] 파일 업로드 완료: {}", url);
            return url;

        } catch (IOException e) {
            log.error("[S3] 파일 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("S3 업로드 실패", e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(fileUrl);

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("[S3] 파일 삭제 완료: {}", key);

        } catch (Exception e) {
            log.warn("[S3] 파일 삭제 실패: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMultiple(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> keys = fileUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(url -> ObjectIdentifier.builder()
                        .key(extractKeyFromUrl(url))
                        .build())
                .toList();

        if (keys.isEmpty()) {
            return;
        }

        try {
            DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(keys).build())
                    .build();

            s3Client.deleteObjects(request);
            log.info("[S3] {} 개 파일 삭제 완료", keys.size());

        } catch (Exception e) {
            log.warn("[S3] 다중 파일 삭제 실패: {}", e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "AWS S3";
    }

    /**
     * S3 URL에서 key 추출
     * https://bucket.s3.region.amazonaws.com/path/file.png -> path/file.png
     */
    private String extractKeyFromUrl(String url) {
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/",
                bucketName, region);
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        // 상대 경로인 경우 그대로 반환
        return url;
    }
}
