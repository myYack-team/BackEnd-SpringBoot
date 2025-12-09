package com.myyak.domain;

import com.myyak.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 약물 임베딩 벡터 테이블
 * OpenAI text-embedding-3-small 모델 사용 (1536차원)
 */
@Entity
@Table(name = "drug_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DrugEmbedding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String itemSeq;  // 품목기준코드 (DrugInfo FK)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String itemName;  // 임베딩 대상 약물명 (검색 편의용)

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] embedding;  // 1536 × 4 bytes (float) = 6,144 bytes

    @Column(nullable = false)
    private Integer dimension;  // 벡터 차원 수 (1536)

    /**
     * byte[] → float[] 변환
     */
    public float[] getEmbeddingVector() {
        if (embedding == null) return null;

        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            int bits = ((embedding[i * 4] & 0xFF) << 24)
                     | ((embedding[i * 4 + 1] & 0xFF) << 16)
                     | ((embedding[i * 4 + 2] & 0xFF) << 8)
                     | (embedding[i * 4 + 3] & 0xFF);
            vector[i] = Float.intBitsToFloat(bits);
        }
        return vector;
    }

    /**
     * float[] → byte[] 변환 후 저장
     */
    public void setEmbeddingVector(float[] vector) {
        this.dimension = vector.length;
        this.embedding = new byte[vector.length * 4];

        for (int i = 0; i < vector.length; i++) {
            int bits = Float.floatToIntBits(vector[i]);
            embedding[i * 4] = (byte) ((bits >> 24) & 0xFF);
            embedding[i * 4 + 1] = (byte) ((bits >> 16) & 0xFF);
            embedding[i * 4 + 2] = (byte) ((bits >> 8) & 0xFF);
            embedding[i * 4 + 3] = (byte) (bits & 0xFF);
        }
    }

    public static DrugEmbedding create(String itemSeq, String itemName, float[] vector) {
        DrugEmbedding embedding = DrugEmbedding.builder()
                .itemSeq(itemSeq)
                .itemName(itemName)
                .dimension(vector.length)
                .build();
        embedding.setEmbeddingVector(vector);
        return embedding;
    }
}
