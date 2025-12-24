package com.myyak.service.ocr;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OcrResult {

    private String fullText;
    private List<TextBlock> blocks;
    private Float confidence;

    @Getter
    @Builder
    public static class TextBlock {
        private String text;
        private BoundingBox boundingBox;
        private Float confidence;
    }

    @Getter
    @Builder
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
    }

    public boolean hasText() {
        return fullText != null && !fullText.isBlank();
    }

    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8f;
    }
}
