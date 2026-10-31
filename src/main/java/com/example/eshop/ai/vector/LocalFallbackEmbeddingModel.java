package com.example.eshop.ai.vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback EmbeddingModel sử dụng thuật toán Vector ngữ nghĩa từ khóa nội bộ (EmbeddingService).
 * Hoạt động tự động khi chưa có GEMINI_API_KEY hoặc khi offline để đảm bảo ứng dụng luôn chạy ổn định.
 */
public class LocalFallbackEmbeddingModel extends AbstractEmbeddingModel {
    private final EmbeddingService embeddingService;

    public LocalFallbackEmbeddingModel(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public float[] embed(Document document) {
        return embeddingService.embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return embeddingService.embed(text);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> list = new ArrayList<>();
        for (String t : texts) {
            list.add(embeddingService.embed(t));
        }
        return list;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(embeddingService.embed(text), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return 128;
    }
}
