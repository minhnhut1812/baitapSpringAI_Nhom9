package com.example.eshop.ai.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cấu hình Vector Database chuẩn Spring AI (SimpleVectorStore).
 * Kết nối với EmbeddingModel để quản lý Document và Similarity Search.
 */
@Configuration
public class VectorStoreConfig {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingService embeddingService) {
        log.info("Khởi tạo EmbeddingModel cho Spring AI VectorStore (Local Semantic Embeddings)");
        return new LocalFallbackEmbeddingModel(embeddingService);
    }

    @Bean
    @Primary
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("Khởi tạo Spring AI SimpleVectorStore chính thức với EmbeddingModel: {}",
                embeddingModel.getClass().getSimpleName());
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
