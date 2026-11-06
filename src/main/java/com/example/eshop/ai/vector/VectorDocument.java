package com.example.eshop.ai.vector;

import java.math.BigDecimal;
import java.util.Map;


public record VectorDocument(
        Long id,
        String name,
        String category,
        BigDecimal price,
        Integer quantity,
        String description,
        String link,
        String contentText,
        float[] embedding,
        Map<String, String> specs
) {

    public double similarityWith(float[] queryEmbedding) {
        if (embedding == null || queryEmbedding == null || embedding.length != queryEmbedding.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < embedding.length; i++) {
            dot += embedding[i] * queryEmbedding[i];
            normA += embedding[i] * embedding[i];
            normB += queryEmbedding[i] * queryEmbedding[i];
        }
        if (normA <= 0.0 || normB <= 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
