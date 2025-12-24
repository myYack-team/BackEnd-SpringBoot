package com.myyak.service.ocr;

public interface OcrClient {

    OcrResult extractText(byte[] imageBytes);

    String getProviderName();
}
