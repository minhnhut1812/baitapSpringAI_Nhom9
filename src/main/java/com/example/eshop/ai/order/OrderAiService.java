package com.example.eshop.ai.order;

import com.example.eshop.entity.Order;
import com.example.eshop.entity.OrderDetail;
import com.example.eshop.entity.OrderStatus;
import com.example.eshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dịch vụ tra cứu đơn hàng & thống kê chi tiêu cho AI Trợ lý
 */
@Service
@RequiredArgsConstructor
public class OrderAiService {
    private final OrderRepository orderRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private final NumberFormat currencyFormatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    public record OrderItemDto(
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            String productLink
    ) {}

    public record OrderDto(
            Long id,
            String orderDate,
            String status,
            String statusLabel,
            BigDecimal totalAmount,
            String paymentMethod,
            String customerName,
            String phone,
            String address,
            List<OrderItemDto> items,
            String detailLink
    ) {}

    public record CustomerOrderStats(
            String username,
            int totalOrders,
            BigDecimal totalSpent,
            BigDecimal averageSpent,
            OrderDto highestOrder,
            OrderDto latestOrder,
            List<OrderDto> recentOrders,
            Map<String, Integer> statusCounts
    ) {}

    /**
     * Nhận diện câu hỏi có liên quan đến Đơn hàng hoặc Thống kê mua sắm của khách
     */
    public boolean isOrderQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.toLowerCase();

        return q.contains("đơn hàng") || q.contains("đơn của tôi") || q.contains("đơn của mình")
                || q.contains("đã mua bao nhiêu") || q.contains("bao nhiêu đơn") || q.contains("bao nhiêu tiền")
                || q.contains("đơn nào cao nhất") || q.contains("đơn đắt nhất") || q.contains("chi tiêu")
                || q.contains("lịch sử mua") || q.contains("lịch sử đặt") || q.contains("tra cứu đơn")
                || q.contains("kiểm tra đơn") || q.contains("tình trạng đơn") || q.contains("đơn số")
                || q.contains("đơn #") || q.contains("đơn gần nhất") || q.contains("tổng tiền đã mua")
                || q.contains("đã chi bao nhiêu") || q.contains("mua những gì");
    }

    /**
     * Trích xuất mã số đơn hàng nếu người dùng hỏi về một đơn cụ thể (vd: "đơn #5", "đơn số 12")
     */
    public Long extractOrderId(String query) {
        if (query == null) return null;
        String q = query.toLowerCase();
        Pattern p = Pattern.compile("(?:đơn(?:\\s*hàng)?(?:\\s*(?:số|mã|#))?|#)\\s*(\\d+)");
        Matcher m = p.matcher(q);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Lấy toàn bộ thống kê và danh sách đơn hàng của khách hàng hiện tại
     */
    @Transactional(readOnly = true)
    public CustomerOrderStats getCustomerStats(String username) {
        if (username == null || username.isBlank()) return null;

        List<Order> orders = orderRepository.findWithDetailsByUsername(username);
        if (orders.isEmpty()) {
            return new CustomerOrderStats(
                    username, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, Collections.emptyList(), Collections.emptyMap()
            );
        }

        int totalOrders = orders.size();
        BigDecimal totalSpent = BigDecimal.ZERO;
        Order highestOrder = orders.get(0);
        Map<String, Integer> statusCounts = new LinkedHashMap<>();

        List<OrderDto> dtoList = new ArrayList<>();
        for (Order o : orders) {
            totalSpent = totalSpent.add(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
            if (o.getTotalAmount() != null && o.getTotalAmount().compareTo(highestOrder.getTotalAmount()) > 0) {
                highestOrder = o;
            }

            String stLabel = formatStatus(o.getStatus());
            statusCounts.put(stLabel, statusCounts.getOrDefault(stLabel, 0) + 1);

            dtoList.add(mapToDto(o));
        }

        BigDecimal averageSpent = totalSpent.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP);
        OrderDto highestDto = mapToDto(highestOrder);
        OrderDto latestDto = dtoList.get(0); // Vì danh sách đã ORDER BY orderDate DESC

        return new CustomerOrderStats(
                username,
                totalOrders,
                totalSpent,
                averageSpent,
                highestDto,
                latestDto,
                dtoList.stream().limit(5).toList(),
                statusCounts
        );
    }

    /**
     * Tra cứu một đơn hàng cụ thể theo ID thuộc quyền sở hữu của username
     */
    @Transactional(readOnly = true)
    public Optional<OrderDto> getSpecificOrder(String username, Long orderId) {
        if (username == null || orderId == null) return Optional.empty();
        return orderRepository.findByIdAndUsernameWithDetails(orderId, username).map(this::mapToDto);
    }

    public String buildStatsPromptData(CustomerOrderStats stats) {
        if (stats == null || stats.totalOrders() == 0) {
            return "THÔNG TIN ĐƠN HÀNG: Khách hàng chưa có đơn hàng nào tại eShop.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== THỐNG KÊ MUA SẮM VÀ ĐƠN HÀNG CỦA KHÁCH HÀNG (").append(stats.username()).append(") ===\n");
        sb.append(String.format("• Tổng số đơn hàng đã mua: %d đơn\n", stats.totalOrders()));
        sb.append(String.format("• Tổng số tiền đã chi tiêu: %s VNĐ\n", currencyFormatter.format(stats.totalSpent())));
        sb.append(String.format("• Giá trị đơn hàng trung bình: %s VNĐ\n", currencyFormatter.format(stats.averageSpent())));

        if (stats.highestOrder() != null) {
            OrderDto h = stats.highestOrder();
            sb.append(String.format("• ĐƠN HÀNG CÓ GIÁ TRỊ CAO NHẤT: Đơn [#%d](%s) với giá trị %s VNĐ (Ngày đặt: %s, Trạng thái: %s)\n",
                    h.id(), h.detailLink(), currencyFormatter.format(h.totalAmount()), h.orderDate(), h.statusLabel()));
            if (!h.items().isEmpty()) {
                sb.append("  Các sản phẩm trong đơn cao nhất này:\n");
                for (OrderItemDto it : h.items()) {
                    sb.append(String.format("    - %dx [%s](%s) (%s VNĐ/cái)\n",
                            it.quantity(), it.productName(), it.productLink(), currencyFormatter.format(it.unitPrice())));
                }
            }
        }

        if (stats.latestOrder() != null) {
            OrderDto l = stats.latestOrder();
            sb.append(String.format("• ĐƠN HÀNG GẦN ĐÂY NHẤT: Đơn [#%d](%s) - %s VNĐ (Trạng thái: %s, Ngày đặt: %s)\n",
                    l.id(), l.detailLink(), currencyFormatter.format(l.totalAmount()), l.statusLabel(), l.orderDate()));
        }

        sb.append("\nPHÂN LOẠI TRẠNG THÁI ĐƠN HÀNG:\n");
        stats.statusCounts().forEach((k, v) -> sb.append(String.format(" - %s: %d đơn\n", k, v)));

        sb.append("\nDANH SÁCH CÁC ĐƠN HÀNG GẦN ĐÂY:\n");
        for (OrderDto o : stats.recentOrders()) {
            sb.append(String.format("• Đơn [#%d](%s) | Ngày: %s | Tổng: %s VNĐ | Trạng thái: %s | PTTT: %s\n",
                    o.id(), o.detailLink(), o.orderDate(), currencyFormatter.format(o.totalAmount()), o.statusLabel(), o.paymentMethod()));
            for (OrderItemDto it : o.items()) {
                sb.append(String.format("    + %dx %s (%s VNĐ)\n", it.quantity(), it.productName(), currencyFormatter.format(it.subtotal())));
            }
        }

        return sb.toString();
    }

    public String buildSpecificOrderPromptData(OrderDto o) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CHI TIẾT ĐƠN HÀNG TRUY VẤN TỪ HỆ THỐNG ===\n");
        sb.append(String.format("Mã đơn hàng: [#%d](%s)\n", o.id(), o.detailLink()));
        sb.append(String.format("Thời gian đặt hàng: %s\n", o.orderDate()));
        sb.append(String.format("Trạng thái hiện tại: %s\n", o.statusLabel()));
        sb.append(String.format("Tổng tiền thanh toán: %s VNĐ\n", currencyFormatter.format(o.totalAmount())));
        sb.append(String.format("Phương thức thanh toán: %s\n", o.paymentMethod()));
        sb.append(String.format("Người nhận: %s (SĐT: %s)\n", o.customerName(), o.phone()));
        sb.append(String.format("Địa chỉ giao nhận: %s\n\n", o.address()));

        sb.append("DANH SÁCH MÓN HÀNG TRONG ĐƠN:\n");
        for (OrderItemDto it : o.items()) {
            sb.append(String.format("• %dx [%s](%s) - Đơn giá: %s VNĐ -> Thành tiền: %s VNĐ\n",
                    it.quantity(), it.productName(), it.productLink(),
                    currencyFormatter.format(it.unitPrice()), currencyFormatter.format(it.subtotal())));
        }

        return sb.toString();
    }

    private OrderDto mapToDto(Order o) {
        List<OrderItemDto> itemDtos = new ArrayList<>();
        if (o.getDetails() != null) {
            for (OrderDetail d : o.getDetails()) {
                String pName = d.getProduct() != null ? d.getProduct().getName() : "Sản phẩm";
                Long pId = d.getProduct() != null ? d.getProduct().getId() : null;
                String link = pId != null ? "/products/" + pId : "#";
                itemDtos.add(new OrderItemDto(
                        pName,
                        d.getQuantity(),
                        d.getUnitPrice(),
                        d.getSubtotal(),
                        link
                ));
            }
        }

        return new OrderDto(
                o.getId(),
                o.getOrderDate() != null ? o.getOrderDate().format(DATE_FORMATTER) : "",
                o.getStatus() != null ? o.getStatus().name() : "NEW",
                formatStatus(o.getStatus()),
                o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO,
                o.getPaymentMethod() != null ? o.getPaymentMethod().name() : "COD",
                o.getCustomerName() != null ? o.getCustomerName() : "",
                o.getPhone() != null ? o.getPhone() : "",
                o.getAddress() != null ? o.getAddress() : "",
                itemDtos,
                "/orders/" + o.getId()
        );
    }

    private String formatStatus(OrderStatus status) {
        if (status == null) return "Chờ xử lý";
        return switch (status) {
            case NEW -> "Mới đặt / Chờ xử lý";
            case PAID -> "Đã thanh toán";
            case SHIPPING -> "Đang giao hàng";
            case DONE -> "Giao thành công / Hoàn thành";
            case CANCELLED -> "Đã hủy";
        };
    }
}
