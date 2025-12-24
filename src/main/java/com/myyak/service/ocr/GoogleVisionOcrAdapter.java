package com.myyak.service.ocr;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "ocr.provider", havingValue = "google-vision", matchIfMissing = false)
public class GoogleVisionOcrAdapter implements OcrClient {

    @Value("${ocr.google-vision.credentials-path:}")
    private String credentialsPath;

    private ImageAnnotatorSettings settings;

    @PostConstruct
    public void init() {
        try {
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                Resource resource;
                if (credentialsPath.startsWith("classpath:")) {
                    resource = new ClassPathResource(credentialsPath.substring("classpath:".length()));
                } else {
                    resource = new FileSystemResource(credentialsPath);
                }

                try (InputStream is = resource.getInputStream()) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(is);
                    settings = ImageAnnotatorSettings.newBuilder()
                            .setCredentialsProvider(() -> credentials)
                            .build();
                    log.info("Google Vision OCR 초기화 완료 (credentials: {})", credentialsPath);
                }
            } else {
                // 환경변수 GOOGLE_APPLICATION_CREDENTIALS 사용
                settings = ImageAnnotatorSettings.newBuilder().build();
                log.info("Google Vision OCR 초기화 완료 (환경변수 사용)");
            }
        } catch (IOException e) {
            log.error("Google Vision OCR 초기화 실패: ", e);
            settings = null;
        }
    }

    @Override
    public OcrResult extractText(byte[] imageBytes) {
        if (settings == null) {
            log.error("Google Vision OCR가 초기화되지 않음");
            return OcrResult.builder()
                    .fullText("")
                    .blocks(List.of())
                    .confidence(0f)
                    .build();
        }

        long startTime = System.currentTimeMillis();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(settings)) {
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image image = Image.newBuilder().setContent(imgBytes).build();

            // 한국어 힌트 설정
            ImageContext imageContext = ImageContext.newBuilder()
                    .addLanguageHints("ko")
                    .addLanguageHints("en")
                    .build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .setImageContext(imageContext)
                    .build();

            BatchAnnotateImagesResponse response = client.batchAnnotateImages(List.of(request));
            AnnotateImageResponse imageResponse = response.getResponses(0);

            if (imageResponse.hasError()) {
                log.error("Google Vision OCR 오류: {}", imageResponse.getError().getMessage());
                return OcrResult.builder()
                        .fullText("")
                        .blocks(List.of())
                        .confidence(0f)
                        .build();
            }

            // 전체 텍스트 추출
            TextAnnotation fullTextAnnotation = imageResponse.getFullTextAnnotation();
            String fullText = fullTextAnnotation.getText();

            // 텍스트 블록 추출
            List<OcrResult.TextBlock> blocks = new ArrayList<>();
            for (EntityAnnotation annotation : imageResponse.getTextAnnotationsList()) {
                if (annotation.getDescription().equals(fullText)) {
                    continue; // 첫 번째는 전체 텍스트이므로 스킵
                }

                BoundingPoly boundingPoly = annotation.getBoundingPoly();
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

                for (Vertex vertex : boundingPoly.getVerticesList()) {
                    minX = Math.min(minX, vertex.getX());
                    minY = Math.min(minY, vertex.getY());
                    maxX = Math.max(maxX, vertex.getX());
                    maxY = Math.max(maxY, vertex.getY());
                }

                blocks.add(OcrResult.TextBlock.builder()
                        .text(annotation.getDescription())
                        .boundingBox(OcrResult.BoundingBox.builder()
                                .x(minX)
                                .y(minY)
                                .width(maxX - minX)
                                .height(maxY - minY)
                                .build())
                        .confidence(annotation.getScore())
                        .build());
            }

            // 전체 신뢰도 계산 (블록별 신뢰도 평균)
            float avgConfidence = 0.85f; // Vision OCR은 신뢰도 제공 안 함, 기본값 사용

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Google Vision OCR 완료: {}ms, 텍스트 길이: {}, 블록 수: {}",
                    elapsed, fullText.length(), blocks.size());

            return OcrResult.builder()
                    .fullText(fullText)
                    .blocks(blocks)
                    .confidence(avgConfidence)
                    .build();

        } catch (Exception e) {
            log.error("Google Vision OCR 실패: ", e);
            return OcrResult.builder()
                    .fullText("")
                    .blocks(List.of())
                    .confidence(0f)
                    .build();
        }
    }

    @Override
    public String getProviderName() {
        return "Google Cloud Vision";
    }
}
