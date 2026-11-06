package com.example.eshop.ai.agent;

import com.example.eshop.ai.vector.ProductVectorStore;
import com.example.eshop.ai.vector.VectorDocument;
import com.example.eshop.cart.Cart;
import com.example.eshop.cart.CartItem;
import com.example.eshop.entity.Order;
import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import com.example.eshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


@Component
@RequiredArgsConstructor
public class ShoppingAgentTools {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAgentTools.class);

    private final Cart cart;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final ProductVectorStore productVectorStore;
    private final NumberFormat currencyFormatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    public record ResolvedProduct(Product product, String reasonHighlight) {}

    @Tool(description = "Thêm sản phẩm vào giỏ hàng của khách hàng theo tên sản phẩm, từ khóa, hoặc đặc điểm (ví dụ: 'điện thoại rẻ nhất', 'laptop gaming mạnh nhất', 'máy ảnh giá rẻ', 'sản phẩm đắt nhất', 'iPhone 15') và số lượng mong muốn.")
    public String addToCart(
            @ToolParam(description = "Tên hoặc đặc điểm sản phẩm khách muốn mua (ví dụ: 'iPhone 15 Pro Max', 'điện thoại rẻ nhất', 'laptop chơi game', 'tai nghe giá rẻ')")
            String productName,
            @ToolParam(description = "Số lượng sản phẩm muốn thêm vào giỏ hàng, mặc định là 1 nếu khách không chỉ định")
            int quantity
    ) {
        log.info("AI Agent gọi tool addToCart: productName={}, quantity={}", productName, quantity);
        if (productName == null || productName.isBlank()) {
            return "Bạn vui lòng cho mình biết tên sản phẩm hoặc đặc điểm bạn muốn mua nhé (ví dụ: *\"Thêm điện thoại rẻ nhất vào giỏ\"*, *\"Thêm 1 cái iPhone 15 vào giỏ\"*)!";
        }
        if (quantity <= 0) {
            quantity = 1;
        }


        ResolvedProduct resolved = resolveProduct(productName.trim());
        if (resolved == null || resolved.product() == null) {
            return "Rất tiếc, mình không tìm thấy sản phẩm nào phù hợp với yêu cầu \"" + productName + "\" tại cửa hàng eShop.";
        }

        Product matchedProduct = resolved.product();

        if (matchedProduct.getQuantity() <= 0) {
            return "Sản phẩm **[" + matchedProduct.getName() + "](/products/" + matchedProduct.getId() + ")**"
                    + (resolved.reasonHighlight() != null ? " *(" + resolved.reasonHighlight() + ")*" : "")
                    + " hiện đã tạm hết hàng trong kho. Bạn tham khảo sản phẩm tương tự khác nhé!";
        }

        if (matchedProduct.getQuantity() < quantity) {
            return "Sản phẩm **[" + matchedProduct.getName() + "](/products/" + matchedProduct.getId() + ")** chỉ còn lại **"
                    + matchedProduct.getQuantity() + "** chiếc trong kho, không đủ số lượng " + quantity + " bạn yêu cầu.";
        }


        cart.add(matchedProduct, quantity);

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 **ĐÃ THÊM VÀO GIỎ HÀNG THÀNH CÔNG!**\n\n");
        sb.append("• **Sản phẩm:** [").append(matchedProduct.getName()).append("](/products/").append(matchedProduct.getId()).append(")\n");
        if (resolved.reasonHighlight() != null && !resolved.reasonHighlight().isBlank()) {
            sb.append("• **Tiêu chí đáp ứng:** *").append(resolved.reasonHighlight()).append("*\n");
        }
        sb.append("• **Đơn giá:** ").append(currencyFormatter.format(matchedProduct.getPrice())).append(" VNĐ\n");
        sb.append("• **Số lượng thêm:** ").append(quantity).append(" chiếc\n");
        sb.append("• **Tổng số lượng trong giỏ hiện tại:** ").append(cart.getTotalQuantity()).append(" món hàng\n");
        sb.append("• **Tổng tiền tạm tính:** ").append(currencyFormatter.format(cart.getTotalAmount())).append(" VNĐ\n\n");
        sb.append("👉 Bạn có thể bấm [Xem giỏ hàng & Thanh toán](/cart) hoặc tiếp tục chọn thêm sản phẩm nhé!");
        return sb.toString();
    }

    @Tool(description = "Xem danh sách các sản phẩm đang có trong giỏ hàng, số lượng, voucher giảm giá và tổng tiền thanh toán.")
    public String viewCart() {
        log.info("AI Agent gọi tool viewCart");
        if (cart.isEmpty()) {
            return "🛒 **Giỏ hàng của bạn đang trống!**\n\nBạn có thể hỏi mình để tìm kiếm và thêm những sản phẩm ưng ý vào giỏ bất cứ lúc nào nhé (ví dụ: *\"Thêm 1 cái iPhone 15 vào giỏ\"*, *\"Thêm điện thoại rẻ nhất vào giỏ\"*).";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 **THÔNG TIN GIỎ HÀNG CỦA BẠN**\n\n");
        sb.append("| Sản phẩm | Số lượng | Đơn giá | Thành tiền |\n");
        sb.append("| :--- | :---: | :---: | :---: |\n");
        for (CartItem item : cart.getItems()) {
            sb.append(String.format("| [%s](/products/%d) | %d | %s đ | %s đ |\n",
                    item.getName(), item.getProductId(), item.getQuantity(),
                    currencyFormatter.format(item.getPrice()),
                    currencyFormatter.format(item.getSubtotal())));
        }
        sb.append("\n");
        sb.append("• **Tổng số lượng món hàng:** ").append(cart.getTotalQuantity()).append("\n");
        sb.append("• **Tạm tính:** ").append(currencyFormatter.format(cart.getSubtotal())).append(" VNĐ\n");

        if (cart.getVoucherCode() != null) {
            sb.append("• **Mã ưu đãi đã áp dụng:** `").append(cart.getVoucherCode()).append("` (-").append(currencyFormatter.format(cart.getDiscountAmount())).append(" VNĐ)\n");
        }

        sb.append("• **TỔNG TIỀN THANH TOÁN:** **").append(currencyFormatter.format(cart.getTotalAmount())).append(" VNĐ**\n\n");
        sb.append("👉 Bạn có thể bấm [Xem chi tiết giỏ hàng & Đặt hàng ngay](/cart) hoặc áp dụng thêm mã giảm giá (ví dụ: *\"Áp mã GIAM10\"*)!");
        return sb.toString();
    }

    @Tool(description = "Áp dụng mã voucher giảm giá vào giỏ hàng của khách hàng (ví dụ GIAM10, CHAOBANMOI, FREESHIP, VIP20).")
    public String applyVoucher(
            @ToolParam(description = "Mã voucher giảm giá muốn áp dụng (ví dụ: 'GIAM10', 'CHAOBANMOI', 'FREESHIP', 'VIP20')")
            String voucherCode
    ) {
        log.info("AI Agent gọi tool applyVoucher: code={}", voucherCode);
        if (voucherCode == null || voucherCode.isBlank()) {
            return "Bạn vui lòng cung cấp mã voucher cần áp dụng nhé (ví dụ: `GIAM10`, `CHAOBANMOI`, `FREESHIP`, `VIP20`).";
        }

        String code = voucherCode.trim().toUpperCase();
        if (cart.isEmpty()) {
            return "Giỏ hàng của bạn đang trống, hãy thêm sản phẩm vào giỏ trước khi áp dụng mã giảm giá nhé!";
        }

        switch (code) {
            case "GIAM10" -> {
                cart.applyVoucher("GIAM10", 10, BigDecimal.ZERO);
                return "🎉 **Áp dụng voucher `GIAM10` thành công!**\n\n"
                        + "• Ưu đãi: Giảm **10%** tổng giá trị giỏ hàng.\n"
                        + "• Số tiền được giảm: **" + currencyFormatter.format(cart.getDiscountAmount()) + " VNĐ**\n"
                        + "• Tổng tiền sau giảm: **" + currencyFormatter.format(cart.getTotalAmount()) + " VNĐ**\n\n"
                        + "👉 Bấm [Xem giỏ hàng & Thanh toán ngay](/cart) để hoàn tất đơn hàng bạn nhé!";
            }
            case "CHAOBANMOI" -> {
                cart.applyVoucher("CHAOBANMOI", 0, new BigDecimal("50000"));
                return "🎉 **Áp dụng voucher `CHAOBANMOI` thành công!**\n\n"
                        + "• Ưu đãi: Giảm ngay **50.000 VNĐ** mừng bạn mới ghé thăm eShop.\n"
                        + "• Số tiền được giảm: **" + currencyFormatter.format(cart.getDiscountAmount()) + " VNĐ**\n"
                        + "• Tổng tiền sau giảm: **" + currencyFormatter.format(cart.getTotalAmount()) + " VNĐ**\n\n"
                        + "👉 Bấm [Xem giỏ hàng & Thanh toán ngay](/cart) để hoàn tất đơn hàng bạn nhé!";
            }
            case "FREESHIP" -> {
                cart.applyVoucher("FREESHIP", 0, new BigDecimal("30000"));
                return "🎉 **Áp dụng voucher `FREESHIP` thành công!**\n\n"
                        + "• Ưu đãi: Hỗ trợ **30.000 VNĐ** phí vận chuyển toàn quốc.\n"
                        + "• Số tiền được giảm: **" + currencyFormatter.format(cart.getDiscountAmount()) + " VNĐ**\n"
                        + "• Tổng tiền sau giảm: **" + currencyFormatter.format(cart.getTotalAmount()) + " VNĐ**\n\n"
                        + "👉 Bấm [Xem giỏ hàng & Thanh toán ngay](/cart) để hoàn tất đơn hàng bạn nhé!";
            }
            case "VIP20" -> {
                cart.applyVoucher("VIP20", 20, BigDecimal.ZERO);
                return "🎉 **Áp dụng voucher `VIP20` thành công!**\n\n"
                        + "• Ưu đãi: Đặc quyền khách VIP giảm **20%** toàn bộ đơn hàng.\n"
                        + "• Số tiền được giảm: **" + currencyFormatter.format(cart.getDiscountAmount()) + " VNĐ**\n"
                        + "• Tổng tiền sau giảm: **" + currencyFormatter.format(cart.getTotalAmount()) + " VNĐ**\n\n"
                        + "👉 Bấm [Xem giỏ hàng & Thanh toán ngay](/cart) để hoàn tất đơn hàng bạn nhé!";
            }
            default -> {
                return "❌ Rất tiếc, mã voucher `" + voucherCode + "` không hợp lệ hoặc đã hết hạn.\n\n"
                        + "🎁 Các mã voucher đang có hiệu lực tại eShop:\n"
                        + "• `GIAM10`: Giảm 10% tổng đơn hàng\n"
                        + "• `CHAOBANMOI`: Giảm ngay 50.000đ cho khách hàng mới\n"
                        + "• `FREESHIP`: Miễn phí giao hàng (giảm 30.000đ)\n"
                        + "• `VIP20`: Giảm 20% cho khách hàng thân thiết";
            }
        }
    }

    @Tool(description = "Hỗ trợ khách hàng hủy một đơn hàng đã đặt theo mã đơn và lý do hủy.")
    public String cancelOrder(
            @ToolParam(description = "Mã số đơn hàng cần hủy (ví dụ: 1, 2, 10)")
            Long orderId,
            @ToolParam(description = "Lý do hủy đơn hàng của khách hàng")
            String reason,
            @ToolParam(description = "Tên đăng nhập (username) của người dùng thực hiện yêu cầu")
            String username
    ) {
        log.info("AI Agent gọi tool cancelOrder: orderId={}, reason={}, username={}", orderId, reason, username);
        if (orderId == null) {
            return "Bạn vui lòng cho mình biết mã đơn hàng cần hủy nhé (ví dụ: *\"Hủy đơn hàng #5\"*).";
        }
        if (username == null || username.isBlank() || "guest".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            return "Để bảo mật và hủy đơn hàng #" + orderId + ", bạn vui lòng [👉 Đăng nhập tài khoản](/login) trước nhé!";
        }

        try {
            Order cancelled = orderService.cancelOrderCustomer(orderId, username, reason != null ? reason : "Khách hàng yêu cầu hủy qua AI Agent");
            return "✅ **ĐÃ HỦY ĐƠN HÀNG #" + orderId + " THÀNH CÔNG!**\n\n"
                    + "• Trạng thái hiện tại: **ĐÃ HỦY (CANCELLED)**\n"
                    + "• Sản phẩm trong đơn đã được tự động hoàn lại số lượng tồn kho.\n"
                    + (reason != null && !reason.isBlank() ? "• Lý do ghi nhận: *" + reason + "*\n\n" : "\n")
                    + "Bạn có thể kiểm tra lại tại [Danh sách đơn hàng của bạn](/orders). Nếu cần hỗ trợ thêm, bạn cứ nhắn mình nhé!";
        } catch (Exception e) {
            log.error("Lỗi khi hủy đơn qua AI Agent: {}", e.getMessage());
            return "⚠️ Không thể hủy đơn hàng #" + orderId + ": " + e.getMessage();
        }
    }


    public ResolvedProduct resolveProduct(String query) {
        if (query == null || query.isBlank()) return null;
        String qLower = query.toLowerCase().trim();


        if (isSuperlativeQuery(qLower)) {
            try {
                ProductVectorStore.ExtremesResult extremes = productVectorStore.findExtremes(qLower);
                if (extremes != null) {
                    VectorDocument targetDoc = null;
                    String highlight = null;

                    List<VectorDocument> inStock = extremes.allCategoryProducts().stream()
                            .filter(d -> d.quantity() > 0)
                            .toList();

                    if (!inStock.isEmpty()) {
                        if ("CHEAPEST".equalsIgnoreCase(extremes.extremeType())) {
                            targetDoc = inStock.get(0);
                            highlight = "Sản phẩm có mức giá tiết kiệm/rẻ nhất trong phân loại " + extremes.categoryName()
                                    + " (" + currencyFormatter.format(targetDoc.price()) + " VNĐ)";
                        } else if ("MOST_EXPENSIVE".equalsIgnoreCase(extremes.extremeType()) || "HIGHEST_SPEC".equalsIgnoreCase(extremes.extremeType())) {
                            targetDoc = inStock.get(inStock.size() - 1);
                            highlight = "Sản phẩm cao cấp/cấu hình mạnh nhất trong phân loại " + extremes.categoryName()
                                    + " (" + currencyFormatter.format(targetDoc.price()) + " VNĐ)";
                        } else if ("LARGEST_SCREEN".equalsIgnoreCase(extremes.extremeType())) {
                            targetDoc = (extremes.targetProduct() != null && extremes.targetProduct().quantity() > 0)
                                    ? extremes.targetProduct() : inStock.get(0);
                            highlight = "Sản phẩm có kích thước màn hình lớn vượt trội trong phân loại " + extremes.categoryName();
                        } else {
                            targetDoc = inStock.get(inStock.size() - 1);
                            highlight = "Sản phẩm nổi bật hàng đầu trong phân loại " + extremes.categoryName();
                        }
                    } else if (extremes.targetProduct() != null) {
                        targetDoc = extremes.targetProduct();
                        highlight = "Sản phẩm tiêu biểu trong phân loại " + extremes.categoryName();
                    }

                    if (targetDoc != null) {
                        Product p = productRepository.findById(targetDoc.id()).orElse(null);
                        if (p != null) {
                            return new ResolvedProduct(p, highlight);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Lỗi khi tìm extremes: {}", e.getMessage());
            }
        }


        if (isPriceSpecQuery(qLower)) {
            try {
                ProductVectorStore.PriceFilterResult priceFilter = productVectorStore.findByPriceQuery(qLower);
                if (priceFilter != null && !priceFilter.matchedProducts().isEmpty()) {
                    List<VectorDocument> inStock = priceFilter.matchedProducts().stream()
                            .filter(d -> d.quantity() > 0)
                            .toList();
                    VectorDocument chosen = null;
                    String highlight = null;
                    if (!inStock.isEmpty()) {
                        if (qLower.contains("rẻ") || qLower.contains("tiết kiệm") || qLower.contains("thấp") || qLower.contains("bình dân")) {
                            chosen = inStock.stream().min(Comparator.comparing(VectorDocument::price)).orElse(inStock.get(0));
                            highlight = "Sản phẩm có giá tốt nhất đáp ứng mức giá mong muốn (" + currencyFormatter.format(chosen.price()) + " VNĐ)";
                        } else if (qLower.contains("cao") || qLower.contains("đắt") || qLower.contains("cao cấp") || qLower.contains("xịn")) {
                            chosen = inStock.stream().max(Comparator.comparing(VectorDocument::price)).orElse(inStock.get(0));
                            highlight = "Sản phẩm cao cấp nhất đáp ứng mức giá mong muốn (" + currencyFormatter.format(chosen.price()) + " VNĐ)";
                        } else {
                            chosen = inStock.get(0);
                            highlight = "Sản phẩm thỏa mãn tiêu chí mức giá (" + currencyFormatter.format(chosen.price()) + " VNĐ)";
                        }
                    } else {
                        chosen = priceFilter.matchedProducts().get(0);
                        highlight = "Sản phẩm theo tiêu chí mức giá";
                    }

                    if (chosen != null) {
                        Product p = productRepository.findById(chosen.id()).orElse(null);
                        if (p != null) {
                            return new ResolvedProduct(p, highlight);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Lỗi khi lọc giá: {}", e.getMessage());
            }
        }


        if (isUsageQualityQuery(qLower)) {
            try {
                ProductVectorStore.UsageRecommendationResult usage = productVectorStore.findForUsage(qLower);
                if (usage != null && !usage.recommendedProducts().isEmpty()) {
                    VectorDocument chosen = usage.recommendedProducts().stream()
                            .filter(d -> d.quantity() > 0)
                            .findFirst()
                            .orElse(usage.recommendedProducts().get(0));
                    Product p = productRepository.findById(chosen.id()).orElse(null);
                    if (p != null) {
                        String highlight = "Đáp ứng chuyên biệt cho nhu cầu: " + usage.intentTitle() + " (" + usage.adviceReason() + ")";
                        return new ResolvedProduct(p, highlight);
                    }
                }
            } catch (Exception e) {
                log.warn("Lỗi khi tìm usage: {}", e.getMessage());
            }
        }


        Product directMatch = findBestProductMatch(query);
        if (directMatch != null) {
            return new ResolvedProduct(directMatch, null);
        }


        try {
            List<VectorDocument> similarDocs = productVectorStore.similaritySearch(query, 1);
            if (!similarDocs.isEmpty()) {
                VectorDocument doc = similarDocs.get(0);
                Product p = productRepository.findById(doc.id()).orElse(null);
                if (p != null) {
                    return new ResolvedProduct(p, "Tìm kiếm tương đồng ngữ nghĩa phù hợp nhất với mô tả: \"" + query + "\"");
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi khi similaritySearch: {}", e.getMessage());
        }

        return null;
    }

    private boolean isSuperlativeQuery(String q) {
        return q.contains("rẻ nhất") || q.contains("thấp nhất") || q.contains("tiết kiệm nhất") || q.contains("bình dân nhất")
                || q.contains("đắt nhất") || q.contains("cao nhất") || q.contains("xịn nhất") || q.contains("cao cấp nhất")
                || q.contains("mạnh nhất") || q.contains("khủng nhất") || q.contains("to nhất") || q.contains("lớn nhất")
                || q.contains("nhỏ nhất") || q.contains("gọn nhất") || q.contains("pin trâu nhất") || q.contains("đẹp nhất")
                || q.contains("tốt nhất") || q.contains("đáng mua nhất");
    }

    private boolean isPriceSpecQuery(String q) {
        return q.contains("giá rẻ") || q.contains("giá thấp") || q.contains("giá cao") || q.contains("bình dân")
                || q.contains("tiết kiệm") || q.contains("cao cấp") || q.contains("dưới") || q.contains("trên")
                || q.contains("tầm") || q.contains("khoảng") || q.contains("triệu") || q.contains("trieu")
                || q.contains("tr") || q.contains("k");
    }

    private boolean isUsageQualityQuery(String q) {
        return q.contains("chơi game") || q.contains("gaming") || q.contains("đồ họa") || q.contains("game")
                || q.contains("sinh viên") || q.contains("học sinh") || q.contains("văn phòng") || q.contains("học tập")
                || q.contains("chụp ảnh") || q.contains("quay video") || q.contains("camera") || q.contains("sống ảo")
                || q.contains("pin trâu") || q.contains("pin khủng") || q.contains("pin lâu")
                || q.contains("mỏng nhẹ") || q.contains("nhỏ gọn");
    }


    private Product findBestProductMatch(String name) {
        List<Product> all = productRepository.findAll();
        String target = name.toLowerCase().trim();


        for (Product p : all) {
            if (p.getName().equalsIgnoreCase(name.trim())) return p;
        }


        for (Product p : all) {
            if (p.getName().toLowerCase().contains(target)) return p;
        }


        String[] keywords = target.split("\\s+");
        Product best = null;
        int maxMatches = 0;
        for (Product p : all) {
            String pName = p.getName().toLowerCase();
            int count = 0;
            for (String kw : keywords) {
                if (kw.length() >= 2 && pName.contains(kw)) count++;
            }
            if (count > maxMatches) {
                maxMatches = count;
                best = p;
            }
        }
        return (maxMatches > 0) ? best : null;
    }
}
