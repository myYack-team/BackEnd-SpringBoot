package com.myyak.service.pdfParserService;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class PdfParserServiceImpl implements PdfParserService {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;

    public PdfParserServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @Override
    public String extractTextFromPdfUrl(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.isBlank()) {
            return null;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                byte[] pdfBytes = downloadPdf(pdfUrl);
                if (pdfBytes == null || pdfBytes.length == 0) {
                    log.warn("PDF 다운로드 실패 (빈 응답): {}", pdfUrl);
                    continue;
                }

                String text = extractTextFromBytes(pdfBytes);
                if (text != null && !text.isBlank()) {
                    return cleanText(text);
                }

            } catch (Exception e) {
                log.warn("PDF 처리 실패 (시도 {}/{}): {} - {}", attempt, MAX_RETRIES, pdfUrl, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("PDF 처리 최종 실패: {}", pdfUrl);
        return null;
    }

    private byte[] downloadPdf(String pdfUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pdfUrl))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.warn("PDF 다운로드 HTTP 오류: {} - status={}", pdfUrl, response.statusCode());
            return null;
        }

        return response.body();
    }

    private String extractTextFromBytes(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return null;
        }

        // NULL 문자 및 제어 문자 제거 후 연속된 공백/개행 정리
        String cleaned = text
                .replaceAll("\\x00", "")  // NULL 문자 제거
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")  // 제어 문자 제거
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return cleaned.isEmpty() ? null : cleaned;
    }
}
