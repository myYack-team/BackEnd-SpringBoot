package com.myyak.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 파일 저장소 클라이언트 인터페이스 (Port)
 * 로컬 파일시스템, S3 등 다양한 저장소 구현체를 교체 가능하게 합니다.
 */
public interface StorageClient {

    /**
     * 파일 업로드
     * @param file 업로드할 파일
     * @param path 저장 경로 (폴더 구조)
     * @param filename 파일명
     * @return 접근 가능한 URL
     */
    String upload(MultipartFile file, String path, String filename);

    /**
     * 파일 삭제
     * @param fileUrl 파일 URL
     */
    void delete(String fileUrl);

    /**
     * 여러 파일 삭제
     * @param fileUrls 파일 URL 목록
     */
    void deleteMultiple(List<String> fileUrls);

    /**
     * 저장소 제공자 이름
     */
    String getProviderName();

    /**
     * 저장소 연결 상태 확인
     * @return 정상 연결 시 true
     */
    boolean isHealthy();
}
