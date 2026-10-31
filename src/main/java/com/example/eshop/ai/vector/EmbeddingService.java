package com.example.eshop.ai.vector;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Dịch vụ tạo vector nhúng (Vector Embeddings) cho sản phẩm và câu hỏi tìm kiếm
 */
@Service
public class EmbeddingService {

    public static final int VECTOR_DIM = 128;

    private static final Set<String> STOP_WORDS = Set.of(
            "và", "của", "cho", "ở", "tại", "với", "là", "thì", "mà", "các", "những",
            "được", "bởi", "vào", "ra", "đến", "trong", "để", "ạ", "nhé", "shop", "ơi"
    );

    private static final Set<String> IMPORTANT_KEYWORDS = Set.of(
            "rẻ", "đắt", "nhất", "so", "sánh", "kích", "thước", "màn", "hình", "chất", "lượng",
            "cấu", "pin", "ram", "chip", "camera", "pro", "max", "ultra", "plus"
    );

    /**
     * Tạo vector embedding 128 chiều được chuẩn hóa (L2 Normalized)
     */
    public float[] embed(String text) {
        float[] vector = new float[VECTOR_DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase().trim()
                .replaceAll("[?,.!:;\"'\\[\\]()#@$%^&*+\\-_/\\\\]", " ");

        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token.isBlank() || STOP_WORDS.contains(token)) continue;

            float weight = computeTokenWeight(token);
            projectToken(token, weight, vector);

            // Tạo thêm n-gram ký tự (tri-grams) để bắt các từ ghép, tên riêng, mã model
            if (token.length() >= 4) {
                for (int i = 0; i <= token.length() - 3; i++) {
                    String ngram = token.substring(i, i + 3);
                    projectToken(ngram, weight * 0.4f, vector);
                }
            }
        }

        // Chuẩn hóa L2 để Cosine Similarity = Dot Product
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }

        if (sumSq > 0.0) {
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < VECTOR_DIM; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    private float computeTokenWeight(String token) {
        // Tăng trọng số cho các từ khóa quan trọng (so sánh, giá, phân loại, thông số)
        if (token.matches(".*\\d+.*")) return 2.5f; // chứa số (giá, inch, dung lượng, model)
        if (IMPORTANT_KEYWORDS.contains(token)) {
            return 2.2f;
        }
        if (token.length() >= 5) return 1.5f;
        return 1.0f;
    }

    private void projectToken(String token, float weight, float[] vector) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        int h1 = hash(bytes, 0x12345678);
        int h2 = hash(bytes, 0x87654321);

        int idx1 = Math.abs(h1) % VECTOR_DIM;
        int idx2 = Math.abs(h2) % VECTOR_DIM;

        float sign1 = (h1 & 1) == 0 ? 1.0f : -1.0f;
        float sign2 = (h2 & 1) == 0 ? 1.0f : -1.0f;

        vector[idx1] += sign1 * weight;
        vector[idx2] += sign2 * weight * 0.7f;
    }

    /**
     * Thuật toán FNV-1a Hash nhanh và phân bố đều
     */
    private int hash(byte[] data, int seed) {
        int h = seed;
        for (byte b : data) {
            h ^= (b & 0xff);
            h *= 16777619;
        }
        return h;
    }
}
