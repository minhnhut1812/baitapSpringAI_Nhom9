package com.example.eshop.ai.vector;

import com.example.eshop.service.SalesAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cơ sở dữ liệu Vector (Vector Database) lưu trữ các tài liệu thống kê bán hàng của eShop:
 * - Đơn hàng nhiều tiền nhất / ít tiền nhất
 * - Sản phẩm mua nhiều nhất / ít nhất (bán chạy / bán chậm)
 * - Ngày bán nhiều đơn nhất / ít đơn nhất (ngày cao điểm / ngày thấp điểm)
 * - Tổng quan doanh thu và số lượng đơn hàng
 */
@Component
@RequiredArgsConstructor
public class SalesAnalyticsVectorStore {
    private static final Logger log = LoggerFactory.getLogger(SalesAnalyticsVectorStore.class);

    private final EmbeddingService embeddingService;
    private final NumberFormat currencyFormatter = NumberFormat.getInstance(new Locale("vi", "VN"));
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record AnalyticsDocument(
            String topic,
            String content,
            float[] embedding
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

    private final List<AnalyticsDocument> indexedDocuments = new ArrayList<>();

    public synchronized void index(SalesAnalyticsService.DashboardStatistics stats) {
        if (stats == null) return;
        indexedDocuments.clear();

        // 1. Phân đoạn 1: Đơn hàng nhiều tiền nhất và ít tiền nhất
        StringBuilder orderSb = new StringBuilder();
        orderSb.append("Thống kê đơn hàng theo giá trị tiền (nhiều tiền nhất và ít tiền nhất): ");
        if (stats.highestValueOrder() != null) {
            var h = stats.highestValueOrder();
            orderSb.append(String.format("Đơn hàng nhiều tiền nhất, có giá trị cao nhất cửa hàng là Đơn #%d với tổng số tiền %s VNĐ, ngày đặt %s, trạng thái: %s. ",
                    h.id(), currencyFormatter.format(h.totalAmount()),
                    h.orderDate() != null ? h.orderDate().format(DATE_FORMATTER) : "", h.status()));
        } else {
            orderSb.append("Chưa có đơn hàng nào nhiều tiền nhất. ");
        }
        if (stats.lowestValueOrder() != null) {
            var l = stats.lowestValueOrder();
            orderSb.append(String.format("Đơn hàng ít tiền nhất, có giá trị thấp nhất cửa hàng là Đơn #%d với tổng số tiền %s VNĐ, ngày đặt %s, trạng thái: %s.",
                    l.id(), currencyFormatter.format(l.totalAmount()),
                    l.orderDate() != null ? l.orderDate().format(DATE_FORMATTER) : "", l.status()));
        } else {
            orderSb.append("Chưa có đơn hàng nào ít tiền nhất.");
        }
        addDocument("DON_HANG_CAO_THAP_NHAT", orderSb.toString());

        // 2. Phân đoạn 2: Sản phẩm mua nhiều nhất và ít nhất (Bán chạy nhất / ít bán nhất)
        StringBuilder prodSb = new StringBuilder();
        prodSb.append("Thống kê sản phẩm bán chạy và mua nhiều nhất, ít nhất: ");
        if (stats.mostPurchasedProduct() != null) {
            var mp = stats.mostPurchasedProduct();
            prodSb.append(String.format("Sản phẩm được mua nhiều nhất, bán chạy nhất cửa hàng là '%s' (Mã SP: %d) với tổng số lượng đã bán là %d sản phẩm, mang lại tổng doanh thu %s VNĐ. ",
                    mp.name(), mp.id(), mp.quantitySold(), currencyFormatter.format(mp.revenue())));
        } else {
            prodSb.append("Chưa có sản phẩm nào được mua nhiều nhất. ");
        }
        if (stats.leastPurchasedProduct() != null) {
            var lp = stats.leastPurchasedProduct();
            prodSb.append(String.format("Sản phẩm được mua ít nhất, bán chậm nhất cửa hàng là '%s' (Mã SP: %d) với số lượng đã bán là %d sản phẩm, tổng doanh thu %s VNĐ.",
                    lp.name(), lp.id(), lp.quantitySold(), currencyFormatter.format(lp.revenue())));
        } else {
            prodSb.append("Chưa có sản phẩm nào bán ít nhất.");
        }
        addDocument("SAN_PHAM_MUA_NHIEU_IT_NHAT", prodSb.toString());

        // 3. Phân đoạn 3: Ngày bán nhiều nhất và ngày bán ít nhất
        StringBuilder daySb = new StringBuilder();
        daySb.append("Thống kê thời gian và ngày bán hàng (ngày bán nhiều đơn nhất, ngày bán ít đơn nhất): ");
        if (stats.busiestSalesDay() != null) {
            var b = stats.busiestSalesDay();
            daySb.append(String.format("Ngày bán nhiều nhất, đông khách nhất, có số lượng đơn cao nhất là ngày %s với %d đơn hàng hợp lệ, đạt tổng doanh thu %s VNĐ trong ngày. ",
                    b.date() != null ? b.date().format(DATE_FORMATTER) : "", b.orderCount(), currencyFormatter.format(b.revenue())));
        } else {
            daySb.append("Chưa có dữ liệu ngày bán nhiều nhất. ");
        }
        if (stats.quietestSalesDay() != null) {
            var q = stats.quietestSalesDay();
            daySb.append(String.format("Ngày bán ít nhất, vắng khách nhất, có số đơn hàng thấp nhất là ngày %s với %d đơn hàng, đạt tổng doanh thu %s VNĐ.",
                    q.date() != null ? q.date().format(DATE_FORMATTER) : "", q.orderCount(), currencyFormatter.format(q.revenue())));
        } else {
            daySb.append("Chưa có dữ liệu ngày bán ít nhất.");
        }
        addDocument("NGAY_BAN_NHIEU_IT_NHAT", daySb.toString());

        // 4. Phân đoạn 4: Tổng quan doanh thu, số đơn, số sản phẩm bán ra
        StringBuilder summarySb = new StringBuilder();
        summarySb.append(String.format("Tổng quan toàn bộ hệ thống eShop: Tổng số đơn hàng hợp lệ (không tính đơn hủy) là %d đơn. ", stats.orderCount()));
        summarySb.append(String.format("Tổng doanh thu toàn sàn đạt: %s VNĐ. ", currencyFormatter.format(stats.totalRevenue())));
        summarySb.append(String.format("Tổng số lượng sản phẩm thực tế đã bán ra là: %d sản phẩm. ", stats.totalUnitsSold()));
        summarySb.append(String.format("Tổng số mặt hàng trong danh mục sản phẩm của cửa hàng là: %d sản phẩm.", stats.productCount()));
        addDocument("TONG_QUAN_DOANH_THU", summarySb.toString());

        log.info("SalesAnalyticsVectorStore đã lập chỉ mục {} tài liệu vector thống kê", indexedDocuments.size());
    }

    private void addDocument(String topic, String content) {
        float[] embedding = embeddingService.embed(content);
        indexedDocuments.add(new AnalyticsDocument(topic, content, embedding));
    }

    /**
     * Tìm kiếm ngữ nghĩa tương đồng (Semantic Search) qua Cosine Similarity trong Vector Database
     */
    public List<String> search(String question, int topK) {
        if (indexedDocuments.isEmpty() || question == null || question.isBlank()) {
            return Collections.emptyList();
        }

        float[] queryEmbedding = embeddingService.embed(question);

        List<AnalyticsDocument> topDocs = indexedDocuments.stream()
                .map(doc -> new AbstractMap.SimpleEntry<>(doc, doc.similarityWith(queryEmbedding)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String qLower = question.toLowerCase();
        Set<String> resultContents = new LinkedHashSet<>();

        if (qLower.contains("đơn") || qLower.contains("tiền") || qLower.contains("giá trị") || qLower.contains("cao nhất") || qLower.contains("thấp nhất")) {
            indexedDocuments.stream().filter(d -> "DON_HANG_CAO_THAP_NHAT".equals(d.topic())).map(AnalyticsDocument::content).forEach(resultContents::add);
        }
        if (qLower.contains("sản phẩm") || qLower.contains("món") || qLower.contains("mua nhiều") || qLower.contains("mua ít") || qLower.contains("bán chạy") || qLower.contains("bán ế") || qLower.contains("chậm")) {
            indexedDocuments.stream().filter(d -> "SAN_PHAM_MUA_NHIEU_IT_NHAT".equals(d.topic())).map(AnalyticsDocument::content).forEach(resultContents::add);
        }
        if (qLower.contains("ngày") || qLower.contains("hôm") || qLower.contains("thời gian") || qLower.contains("thứ")) {
            indexedDocuments.stream().filter(d -> "NGAY_BAN_NHIEU_IT_NHAT".equals(d.topic())).map(AnalyticsDocument::content).forEach(resultContents::add);
        }

        for (AnalyticsDocument doc : topDocs) {
            resultContents.add(doc.content());
        }

        indexedDocuments.stream().filter(d -> "TONG_QUAN_DOANH_THU".equals(d.topic())).map(AnalyticsDocument::content).forEach(resultContents::add);

        return new ArrayList<>(resultContents);
    }
}
