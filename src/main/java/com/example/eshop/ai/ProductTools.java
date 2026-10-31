package com.example.eshop.ai;

import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Các công cụ để AI tra cứu dữ liệu thật của cửa hàng (tránh bịa sản phẩm/giá). */
@Component
@RequiredArgsConstructor
public class ProductTools {
    private final ProductRepository productRepository;

    public record ProductInfo(Long id, String name, String category, BigDecimal price,
                              Integer quantity, String description, String link) {}

    @Tool(description = "Tìm sản phẩm trong cửa hàng theo từ khoá tên. Bỏ trống keyword để lấy sản phẩm mới nhất.")
    @Transactional(readOnly = true)
    public List<ProductInfo> searchProducts(
            @ToolParam(description = "Từ khoá tên sản phẩm, có thể để trống", required = false) String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return productRepository.search(kw, null, PageRequest.of(0, 8)).stream().map(this::toInfo).toList();
    }

    @Tool(description = "Lấy chi tiết một sản phẩm theo id.")
    @Transactional(readOnly = true)
    public ProductInfo getProduct(@ToolParam(description = "ID sản phẩm") Long id) {
        return productRepository.findById(id).map(this::toInfo).orElse(null);
    }

    private ProductInfo toInfo(Product p) {
        return new ProductInfo(p.getId(), p.getName(), p.getCategory().getName(), p.getPrice(),
                p.getQuantity(), p.getDescription(), "/products/" + p.getId());
    }
}
