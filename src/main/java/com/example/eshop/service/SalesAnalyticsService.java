package com.example.eshop.service;

import com.example.eshop.ai.vector.SalesAnalyticsVectorStore;
import com.example.eshop.entity.Order;
import com.example.eshop.entity.OrderDetail;
import com.example.eshop.entity.OrderStatus;
import com.example.eshop.entity.Product;
import com.example.eshop.repository.OrderRepository;
import com.example.eshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesAnalyticsService {
    private static final String AI_SYSTEM_PROMPT = """
            Bạn là trợ lý phân tích bán hàng nội bộ của eShop. Trả lời bằng tiếng Việt, ngắn gọn và chỉ dựa trên dữ liệu được truy xuất từ Vector Database bên dưới.
            Dùng đúng mã đơn, tên sản phẩm, số lượng, ngày và số tiền được cung cấp; không suy đoán hoặc tự tạo số liệu.
            Nếu dữ liệu không đủ để trả lời, hãy nói rõ điều đó. Đơn đã hủy không nằm trong thống kê.
            """;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SalesAnalyticsVectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;

    public record OrderSales(Long id, LocalDateTime orderDate, String status, BigDecimal totalAmount) {}
    public record ProductSales(Long id, String name, long quantitySold, BigDecimal revenue) {}
    public record DailySales(LocalDate date, int orderCount, BigDecimal revenue) {}

    public record DashboardStatistics(
            int orderCount,
            BigDecimal totalRevenue,
            long totalUnitsSold,
            int productCount,
            OrderSales highestValueOrder,
            OrderSales lowestValueOrder,
            ProductSales mostPurchasedProduct,
            ProductSales leastPurchasedProduct,
            DailySales busiestSalesDay,
            DailySales quietestSalesDay
    ) {}

    @Transactional(readOnly = true)
    public DashboardStatistics getDashboardStatistics() {
        List<Product> products = productRepository.findAllWithCategory();
        Map<Long, ProductTotals> productTotals = new HashMap<>();
        for (Product product : products) {
            productTotals.put(product.getId(), new ProductTotals(product));
        }

        List<Order> orders = orderRepository.findAllWithDetailsByOrderDateDesc();
        List<OrderSales> eligibleOrders = new ArrayList<>();
        Map<LocalDate, DailyTotals> dailyTotals = new HashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalUnitsSold = 0;

        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }

            BigDecimal orderTotal = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            eligibleOrders.add(new OrderSales(order.getId(), order.getOrderDate(),
                    order.getStatus() == null ? "Không rõ" : order.getStatus().name(), orderTotal));
            totalRevenue = totalRevenue.add(orderTotal);

            if (order.getOrderDate() != null) {
                DailyTotals daily = dailyTotals.computeIfAbsent(order.getOrderDate().toLocalDate(), ignored -> new DailyTotals());
                daily.orderCount++;
                daily.revenue = daily.revenue.add(orderTotal);
            }

            for (OrderDetail detail : order.getDetails()) {
                totalUnitsSold += detail.getQuantity();
                Product product = detail.getProduct();
                ProductTotals totals = product == null ? null : productTotals.get(product.getId());
                if (totals != null) {
                    totals.quantitySold += detail.getQuantity();
                    totals.revenue = totals.revenue.add(detail.getSubtotal());
                }
            }
        }

        Comparator<OrderSales> orderByAmount = Comparator.comparing(OrderSales::totalAmount)
                .thenComparing(OrderSales::id, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<ProductSales> productByUnits = Comparator.comparingLong(ProductSales::quantitySold)
                .thenComparing(ProductSales::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ProductSales::id);
        Comparator<DailySales> dayByOrders = Comparator.comparingInt(DailySales::orderCount)
                .thenComparing(DailySales::date);

        List<ProductSales> productSales = productTotals.values().stream()
                .map(ProductTotals::toMetric)
                .sorted(productByUnits)
                .toList();
        List<DailySales> days = dailyTotals.entrySet().stream()
                .map(entry -> new DailySales(entry.getKey(), entry.getValue().orderCount, entry.getValue().revenue))
                .sorted(dayByOrders)
                .toList();

        DashboardStatistics statistics = new DashboardStatistics(
                eligibleOrders.size(),
                totalRevenue,
                totalUnitsSold,
                products.size(),
                eligibleOrders.stream().min(orderByAmount.reversed()).orElse(null),
                eligibleOrders.stream().min(orderByAmount).orElse(null),
                productSales.isEmpty() ? null : productSales.get(productSales.size() - 1),
                productSales.isEmpty() ? null : productSales.get(0),
                days.isEmpty() ? null : days.get(days.size() - 1),
                days.isEmpty() ? null : days.get(0)
        );
        vectorStore.index(statistics);
        return statistics;
    }

    public String answerQuestion(String question) {
        List<String> retrievedData = vectorStore.search(question, 4);
        if (retrievedData.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy dữ liệu thống kê phù hợp trong Vector Database.");
        }
        String context = String.join("\n", retrievedData);
        String answer = chatClientBuilder.build().prompt()
                .system(AI_SYSTEM_PROMPT + "\nDỮ LIỆU THỐNG KÊ TRUY XUẤT TỪ VECTOR DATABASE:\n" + context)
                .user(question)
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("AI không trả về nội dung phân tích.");
        }
        return answer;
    }

    private static final class ProductTotals {
        private final Product product;
        private long quantitySold;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductTotals(Product product) {
            this.product = product;
        }

        private ProductSales toMetric() {
            return new ProductSales(product.getId(), product.getName(), quantitySold, revenue);
        }
    }

    private static final class DailyTotals {
        private int orderCount;
        private BigDecimal revenue = BigDecimal.ZERO;
    }
}
