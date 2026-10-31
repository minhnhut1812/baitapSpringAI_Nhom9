package com.example.eshop.ai;

import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Tra cứu sản phẩm thật trong DB để làm ngữ cảnh duy nhất cho chatbot. */
@Component
@RequiredArgsConstructor
public class ProductLookup {
    private final ProductRepository productRepository;

    private static final Set<String> STOP_WORDS = Set.of(
            "tìm", "mua", "có", "không", "giá", "bán", "cho", "mình", "em", "anh",
            "xem", "shop", "cửa", "hàng", "sản", "phẩm", "loại", "nào", "ạ", "nhé", "với", "tư", "vấn",
            "hỏi", "chiếc", "cái", "thế", "bao", "nhiêu", "tiền"
    );

    public record ProductInfo(Long id, String name, String category, BigDecimal price,
                              Integer quantity, String description, String link) {}

    @Transactional(readOnly = true)
    public List<ProductInfo> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return productRepository.search(null, null, PageRequest.of(0, 8))
                    .stream().map(this::toInfo).toList();
        }
        String kw = keyword.trim();
        List<ProductInfo> results = productRepository.search(kw, null, PageRequest.of(0, 8))
                .stream().map(this::toInfo).toList();

        if (results.isEmpty() && kw.contains(" ")) {
            // Thử tìm theo từng từ khoá riêng biệt (ví dụ: "điện thoại oppo" -> "oppo")
            String[] tokens = kw.split("\\s+");
            for (String token : tokens) {
                if (token.length() >= 3) {
                    List<ProductInfo> tokenResults = productRepository.search(token, null, PageRequest.of(0, 8))
                            .stream().map(this::toInfo).toList();
                    if (!tokenResults.isEmpty()) {
                        return tokenResults;
                    }
                }
            }
        }
        return results;
    }

    /** Tìm nhanh sản phẩm dựa trên tin nhắn trực tiếp của khách để bỏ qua bước classify giúp AI phản hồi cực nhanh */
    @Transactional(readOnly = true)
    public List<ProductInfo> findDirect(String message) {
        if (message == null || message.isBlank()) return List.of();
        String clean = message.toLowerCase()
                .replaceAll("[?,.!:;\"'\\[\\]()]", " ")
                .trim();

        String[] words = clean.split("\\s+");
        for (String w : words) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                List<ProductInfo> list = search(w);
                if (!list.isEmpty()) {
                    return list;
                }
            }
        }
        return List.of();
    }

    private ProductInfo toInfo(Product p) {
        return new ProductInfo(p.getId(), p.getName(), p.getCategory().getName(), p.getPrice(),
                p.getQuantity(), p.getDescription(), "/products/" + p.getId());
    }
}
