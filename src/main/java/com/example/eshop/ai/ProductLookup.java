package com.example.eshop.ai;

import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Tra cứu sản phẩm thật trong DB để làm ngữ cảnh duy nhất cho chatbot. */
@Component
@RequiredArgsConstructor
public class ProductLookup {
    private final ProductRepository productRepository;

    public record ProductInfo(Long id, String name, String category, BigDecimal price,
                              Integer quantity, String description, String link) {}

    @Transactional(readOnly = true)
    public List<ProductInfo> search(String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return productRepository.search(kw, null, PageRequest.of(0, 8)).stream().map(this::toInfo).toList();
    }

    private ProductInfo toInfo(Product p) {
        return new ProductInfo(p.getId(), p.getName(), p.getCategory().getName(), p.getPrice(),
                p.getQuantity(), p.getDescription(), "/products/" + p.getId());
    }
}
