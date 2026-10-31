package com.example.eshop.ai.vector;

import com.example.eshop.entity.Product;
import com.example.eshop.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cơ sở dữ liệu Vector (Vector Database) lưu trữ nhúng ngữ nghĩa của các sản phẩm
 * Hỗ trợ Semantic Search, So sánh đa chiều (giá, chất lượng, kích thước) và Truy vấn Cái nhất (Superlatives).
 */
@Component
@RequiredArgsConstructor
public class ProductVectorStore {
    private static final Logger log = LoggerFactory.getLogger(ProductVectorStore.class);

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;

    private final List<VectorDocument> indexedDocuments = new ArrayList<>();
    private final NumberFormat currencyFormatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    public record ExtremesResult(
            String categoryName,
            String extremeType,
            VectorDocument targetProduct,
            VectorDocument mostExpensive,
            VectorDocument cheapest,
            List<VectorDocument> allCategoryProducts,
            BigDecimal averagePrice
    ) {}

    public record ComparisonResult(
            List<VectorDocument> products,
            String comparisonData
    ) {}

    public record PriceFilterResult(
            String filterType, // "UNDER", "ABOVE", "BETWEEN", "AROUND"
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String targetScope,
            List<VectorDocument> matchedProducts,
            int totalCount,
            BigDecimal averagePrice,
            VectorDocument mostAffordable,
            VectorDocument topTier,
            VectorDocument cheapestOverall
    ) {}

    public record StockCheckResult(
            String queryProduct,
            List<VectorDocument> targetProducts,
            boolean hasInStock,
            List<VectorDocument> inStockProducts,
            List<VectorDocument> outOfStockProducts,
            List<VectorDocument> alternatives
    ) {}

    public record UsageRecommendationResult(
            String intentType,
            String intentTitle,
            List<VectorDocument> recommendedProducts,
            String adviceReason
    ) {}

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * Nạp và tạo vector nhúng (Index) cho toàn bộ sản phẩm trong Database
     */
    @Transactional(readOnly = true)
    public synchronized void refresh() {
        try {
            List<Product> products = productRepository.findAllWithCategory();
            indexedDocuments.clear();

            for (Product p : products) {
                String catName = p.getCategory() != null ? p.getCategory().getName() : "Khác";
                Map<String, String> specs = extractSpecs(p.getName(), p.getDescription(), catName);

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Tên sản phẩm: ").append(p.getName()).append(". ");
                contentBuilder.append("Phân loại danh mục: ").append(catName).append(". ");
                contentBuilder.append("Giá bán: ").append(currencyFormatter.format(p.getPrice())).append(" VNĐ. ");
                contentBuilder.append("Số lượng tồn kho: ").append(p.getQuantity()).append(". ");

                specs.forEach((k, v) -> contentBuilder.append(k).append(": ").append(v).append(". "));

                if (p.getDescription() != null && !p.getDescription().isBlank()) {
                    contentBuilder.append("Mô tả chi tiết: ").append(p.getDescription());
                }

                String contentText = contentBuilder.toString();
                float[] embedding = embeddingService.embed(contentText);

                indexedDocuments.add(new VectorDocument(
                        p.getId(),
                        p.getName(),
                        catName,
                        p.getPrice(),
                        p.getQuantity(),
                        p.getDescription(),
                        "/products/" + p.getId(),
                        contentText,
                        embedding,
                        specs
                ));
            }
            log.info("Vector Database đã lập chỉ mục {} sản phẩm", indexedDocuments.size());
        } catch (Exception e) {
            log.error("Lỗi khi lập chỉ mục Vector Database: {}", e.getMessage());
        }
    }

    /**
     * Tìm kiếm ngữ nghĩa tương đồng (Semantic Vector Search) bằng Cosine Similarity
     */
    public List<VectorDocument> similaritySearch(String query, int topK) {
        if (indexedDocuments.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        float[] queryEmbedding = embeddingService.embed(query);

        return indexedDocuments.stream()
                .map(doc -> new AbstractMap.SimpleEntry<>(doc, doc.similarityWith(queryEmbedding)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .filter(e -> e.getValue() > 0.05)
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Nhận diện và trích xuất các sản phẩm để SO SÁNH (Giá, Kích thước, Chất lượng/Cấu hình)
     */
    public ComparisonResult findForComparison(String query) {
        if (query == null || indexedDocuments.isEmpty()) {
            return new ComparisonResult(Collections.emptyList(), "");
        }

        String qLower = query.toLowerCase();
        Set<VectorDocument> selected = new LinkedHashSet<>();

        // 1. Quét theo tên sản phẩm có mặt trực tiếp trong câu hỏi
        for (VectorDocument doc : indexedDocuments) {
            String nameLower = doc.name().toLowerCase();
            // Lấy từ khóa chính (vd: "iPhone 15", "Oppo Reno", "Galaxy S24", "Dell XPS")
            String cleanName = nameLower.replaceAll("\\(.*?\\)", "").trim();
            if (qLower.contains(cleanName) || isSubMatch(qLower, cleanName)) {
                selected.add(doc);
            }
        }

        // 2. Nếu chưa đủ ít nhất 2 sản phẩm, dùng Semantic Vector Search để tìm các sản phẩm tương quan nhất
        if (selected.size() < 2) {
            List<VectorDocument> topMatches = similaritySearch(query, 4);
            for (VectorDocument doc : topMatches) {
                selected.add(doc);
                if (selected.size() >= 3) break;
            }
        }

        List<VectorDocument> resultList = new ArrayList<>(selected);
        if (resultList.size() < 2) {
            return new ComparisonResult(resultList, "");
        }

        // Xây dựng dữ liệu so sánh chi tiết
        StringBuilder sb = new StringBuilder();
        sb.append("=== DỮ LIỆU SO SÁNH CHI TIẾT TỪ VECTOR DATABASE ===\n");
        for (int i = 0; i < resultList.size(); i++) {
            VectorDocument d = resultList.get(i);
            sb.append(String.format("Sản phẩm %d: [%s](%s)\n", (i + 1), d.name(), d.link()));
            sb.append(String.format(" - Danh mục: %s\n", d.category()));
            sb.append(String.format(" - Giá bán: %s VNĐ\n", currencyFormatter.format(d.price())));
            sb.append(String.format(" - Tồn kho: %d\n", d.quantity()));
            d.specs().forEach((k, v) -> sb.append(String.format(" - %s: %s\n", k, v)));
            if (d.description() != null && !d.description().isBlank()) {
                sb.append(String.format(" - Đánh giá/Mô tả: %s\n", d.description()));
            }
            sb.append("\n");
        }

        // Tính chênh lệch giá giữa 2 sản phẩm đầu tiên
        if (resultList.size() >= 2) {
            VectorDocument d1 = resultList.get(0);
            VectorDocument d2 = resultList.get(1);
            BigDecimal diff = d1.price().subtract(d2.price()).abs();
            String compareText = d1.price().compareTo(d2.price()) > 0
                    ? String.format("[%s](%s) đắt hơn [%s](%s) là %s VNĐ", d1.name(), d1.link(), d2.name(), d2.link(), currencyFormatter.format(diff))
                    : (d1.price().compareTo(d2.price()) < 0
                    ? String.format("[%s](%s) rẻ hơn [%s](%s) là %s VNĐ", d1.name(), d1.link(), d2.name(), d2.link(), currencyFormatter.format(diff))
                    : "Hai sản phẩm có mức giá bằng nhau");
            sb.append("Chênh lệch giá: ").append(compareText).append("\n");
        }

        return new ComparisonResult(resultList, sb.toString());
    }

    /**
     * Nhận diện và truy vấn CÁI NHẤT TRONG CÁC PHÂN LOẠI (Rẻ nhất, Đắt nhất, Kích thước to nhất, Cấu hình mạnh nhất)
     */
    public ExtremesResult findExtremes(String query) {
        if (query == null || indexedDocuments.isEmpty()) return null;

        String qLower = query.toLowerCase();

        // Xác định loại "Cái nhất"
        String extremeType = null;
        if (qLower.contains("rẻ nhất") || qLower.contains("thấp nhất") || qLower.contains("tiết kiệm nhất") || qLower.contains("bình dân nhất")) {
            extremeType = "CHEAPEST";
        } else if (qLower.contains("đắt nhất") || qLower.contains("cao nhất") || qLower.contains("đắt tiền nhất") || qLower.contains("xịn nhất") || qLower.contains("cao cấp nhất")) {
            extremeType = "MOST_EXPENSIVE";
        } else if (qLower.contains("to nhất") || qLower.contains("lớn nhất") || qLower.contains("màn hình to") || qLower.contains("màn to")) {
            extremeType = "LARGEST_SCREEN";
        } else if (qLower.contains("mạnh nhất") || qLower.contains("khủng nhất") || qLower.contains("cấu hình cao nhất") || qLower.contains("chơi game tốt nhất")) {
            extremeType = "HIGHEST_SPEC";
        } else if (qLower.contains("tốt nhất") || qLower.contains("đáng mua nhất") || qLower.contains("nhiều nhất")) {
            extremeType = "BEST_OVERALL";
        }

        if (extremeType == null) return null;

        // Xác định danh mục sản phẩm được hỏi
        String matchedCategory = null;
        for (VectorDocument doc : indexedDocuments) {
            String cat = doc.category().toLowerCase();
            if (qLower.contains(cat) || (cat.contains("thoại") && qLower.contains("điện thoại"))
                    || (cat.contains("laptop") && (qLower.contains("laptop") || qLower.contains("máy tính")))
                    || (cat.contains("phụ kiện") && qLower.contains("phụ kiện"))) {
                matchedCategory = doc.category();
                break;
            }
        }

        final String finalCategory = matchedCategory;
        // Lọc danh sách sản phẩm thuộc danh mục (nếu có), hoặc toàn bộ cửa hàng
        List<VectorDocument> pool = finalCategory != null
                ? indexedDocuments.stream().filter(d -> d.category().equalsIgnoreCase(finalCategory)).toList()
                : indexedDocuments;

        if (pool.isEmpty()) return null;

        // Sắp xếp theo giá tăng dần
        List<VectorDocument> sortedByPrice = pool.stream()
                .sorted(Comparator.comparing(VectorDocument::price))
                .toList();

        VectorDocument cheapest = sortedByPrice.get(0);
        VectorDocument mostExpensive = sortedByPrice.get(sortedByPrice.size() - 1);

        BigDecimal sum = BigDecimal.ZERO;
        for (VectorDocument d : pool) {
            sum = sum.add(d.price());
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(pool.size()), 0, RoundingMode.HALF_UP);

        VectorDocument targetProduct = switch (extremeType) {
            case "CHEAPEST" -> cheapest;
            case "MOST_EXPENSIVE", "HIGHEST_SPEC" -> mostExpensive;
            case "LARGEST_SCREEN" -> findLargestScreen(pool);
            default -> mostExpensive;
        };

        return new ExtremesResult(
                matchedCategory != null ? matchedCategory : "Toàn bộ cửa hàng",
                extremeType,
                targetProduct,
                mostExpensive,
                cheapest,
                sortedByPrice,
                avg
        );
    }

    private VectorDocument findLargestScreen(List<VectorDocument> pool) {
        VectorDocument best = pool.get(0);
        double maxInches = 0.0;
        Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(inch|\")", Pattern.CASE_INSENSITIVE);

        for (VectorDocument doc : pool) {
            String text = doc.name() + " " + (doc.description() != null ? doc.description() : "");
            Matcher m = p.matcher(text);
            if (m.find()) {
                try {
                    double inches = Double.parseDouble(m.group(1));
                    if (inches > maxInches) {
                        maxInches = inches;
                        best = doc;
                    }
                } catch (Exception ignored) {}
            }
        }
        return best;
    }

    private boolean isSubMatch(String query, String name) {
        String[] parts = name.split("\\s+");
        if (parts.length < 2) return false;
        int matchCount = 0;
        for (String part : parts) {
            if (part.length() >= 3 && query.contains(part)) {
                matchCount++;
            }
        }
        return matchCount >= 2;
    }

    /**
     * Tự động trích xuất và chuẩn hóa thông số (Kích thước màn hình, Cấu hình, Chất lượng hoàn thiện)
     */
    private Map<String, String> extractSpecs(String name, String desc, String category) {
        Map<String, String> specs = new LinkedHashMap<>();
        String text = (name + " " + (desc != null ? desc : "")).toLowerCase();

        // 1. Kích thước màn hình
        Pattern screenPattern = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(inch|\"|inh)", Pattern.CASE_INSENSITIVE);
        Matcher mScreen = screenPattern.matcher(text);
        if (mScreen.find()) {
            specs.put("Kích thước màn hình", mScreen.group(1) + " inch");
        } else if (text.contains("pro max") || text.contains("plus") || text.contains("ultra")) {
            specs.put("Kích thước màn hình", "Màn hình lớn (~6.7 - 6.8 inch)");
        } else if (text.contains("mini") || text.contains("nhỏ gọn")) {
            specs.put("Kích thước màn hình", "Nhỏ gọn (~5.4 - 6.1 inch)");
        } else if (category.toLowerCase().contains("laptop")) {
            specs.put("Kích thước màn hình", "14 - 15.6 inch");
        }

        // 2. Cấu hình phần cứng (RAM & Bộ nhớ)
        Pattern romPattern = Pattern.compile("(\\d{2,4})\\s*(gb|tb)", Pattern.CASE_INSENSITIVE);
        Matcher mRom = romPattern.matcher(text);
        if (mRom.find()) {
            specs.put("Dung lượng bộ nhớ", mRom.group(1) + mRom.group(2).toUpperCase());
        }

        // 3. Vi xử lý & Hiệu năng
        if (text.contains("a17") || text.contains("a16") || text.contains("a15")) {
            specs.put("Cấu hình chip", "Apple Bionic/A-Series hiệu năng hàng đầu");
        } else if (text.contains("snapdragon 8") || text.contains("snapdragon")) {
            specs.put("Cấu hình chip", "Snapdragon cao cấp xử lý đồ họa mạnh mẽ");
        } else if (text.contains("dimensity")) {
            specs.put("Cấu hình chip", "MediaTek Dimensity tối ưu pin và nhiệt độ");
        } else if (text.contains("core i7") || text.contains("core i5") || text.contains("m2") || text.contains("m3")) {
            specs.put("Cấu hình CPU", "Vi xử lý đa nhân tốc độ cao cho làm việc chuyên nghiệp");
        }

        // 4. Chất lượng hoàn thiện & Vật liệu
        if (text.contains("titan") || text.contains("titanium")) {
            specs.put("Chất lượng hoàn thiện", "Khung viền Titan siêu bền, nhẹ, cao cấp");
        } else if (text.contains("nhôm") || text.contains("kim loại")) {
            specs.put("Chất lượng hoàn thiện", "Kim loại nguyên khối sang trọng, chắc chắn");
        } else if (text.contains("kính")) {
            specs.put("Chất lượng hoàn thiện", "Mặt lưng kính cường lực cao cấp chống trầy");
        } else {
            specs.put("Chất lượng hoàn thiện", "Gia công tinh xảo, độ bền chuẩn chính hãng");
        }

        return specs;
    }

    /**
     * Nhận diện và lọc sản phẩm theo mức giá (Dưới giá, Trên giá, Khoảng giá, Tầm giá)
     */
    public PriceFilterResult findByPriceQuery(String query) {
        if (query == null || indexedDocuments.isEmpty()) return null;
        String q = query.toLowerCase().trim();

        String filterType = null;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        // 1. Kiểm tra Khoảng giá (Range): từ X đến/tới Y hoặc X - Y triệu
        Pattern rangePat = Pattern.compile("(?:từ\\s*)?(\\d+(?:[.,]\\d+)?\\s*(?:triệu|trieu|tr|k|m|nghìn|ngàn)?)\\s*(?:đến|tới|-)\\s*(\\d+(?:[.,]\\d+)?\\s*(?:triệu|trieu|tr|k|m|nghìn|ngàn))");
        Matcher mRange = rangePat.matcher(q);
        if (mRange.find()) {
            String s1 = mRange.group(1).trim();
            String s2 = mRange.group(2).trim();
            BigDecimal p2 = parseSinglePrice(s2);
            BigDecimal p1 = parseSinglePrice(s1);
            if (p1 == null && s1.matches("\\d+(?:[.,]\\d+)?")) {
                p1 = parseWithUnit(Double.parseDouble(s1.replace(",", ".")), s2);
            }
            if (p1 != null && p2 != null) {
                filterType = "BETWEEN";
                minPrice = p1.min(p2);
                maxPrice = p1.max(p2);
            }
        }

        // 2. Kiểm tra Dưới giá / Rẻ hơn / Tối đa / Ngân sách
        if (filterType == null) {
            Pattern underPat = Pattern.compile("(?:dưới|nhỏ hơn|bé hơn|thấp hơn|rẻ hơn|tối đa|không quá|ngân sách|tài chính(?:\\s*dưới)?|<=|<)\\s*(\\d+(?:[.,]\\d+)?(?:\\s*(?:triệu|trieu|tr|k|m|nghìn|ngàn)|tr\\d+)?|\\d{1,3}(?:[.,]\\d{3})+|\\d{6,})");
            Matcher mUnder = underPat.matcher(q);
            if (mUnder.find()) {
                BigDecimal p = parseSinglePrice(mUnder.group(1));
                if (p != null) {
                    filterType = "UNDER";
                    minPrice = BigDecimal.ZERO;
                    maxPrice = p;
                }
            }
        }

        // 3. Kiểm tra Trên giá / Đắt hơn / Tối thiểu / Từ ... trở lên
        if (filterType == null) {
            Pattern abovePat = Pattern.compile("(?:trên|lớn hơn|cao hơn|đắt hơn|nhiều hơn|tối thiểu|từ|>=|>)\\s*(\\d+(?:[.,]\\d+)?(?:\\s*(?:triệu|trieu|tr|k|m|nghìn|ngàn)|tr\\d+)?|\\d{1,3}(?:[.,]\\d{3})+|\\d{6,})(?:\\s*(?:trở lên|trở đi))?");
            Matcher mAbove = abovePat.matcher(q);
            if (mAbove.find()) {
                BigDecimal p = parseSinglePrice(mAbove.group(1));
                if (p != null) {
                    filterType = "ABOVE";
                    minPrice = p;
                    maxPrice = BigDecimal.valueOf(1_000_000_000L); // 1 tỷ
                }
            }
        }

        // 4. Kiểm tra Khoảng / Tầm giá (Around ± 15%)
        if (filterType == null) {
            Pattern aroundPat = Pattern.compile("(?:khoảng|tầm(?:\\s*giá)?|xung quanh)\\s*(\\d+(?:[.,]\\d+)?(?:\\s*(?:triệu|trieu|tr|k|m|nghìn|ngàn)|tr\\d+)?|\\d{1,3}(?:[.,]\\d{3})+|\\d{6,})");
            Matcher mAround = aroundPat.matcher(q);
            if (mAround.find()) {
                BigDecimal p = parseSinglePrice(mAround.group(1));
                if (p != null) {
                    filterType = "AROUND";
                    minPrice = p.multiply(BigDecimal.valueOf(0.85));
                    maxPrice = p.multiply(BigDecimal.valueOf(1.15));
                }
            }
        }

        if (filterType == null || (minPrice == null && maxPrice == null)) {
            return null;
        }

        String targetScope = detectTargetScope(q);

        List<VectorDocument> pool = targetScope.equals("Toàn bộ cửa hàng")
                ? indexedDocuments
                : indexedDocuments.stream().filter(d -> matchesScope(d, targetScope)).toList();

        VectorDocument cheapestOverall = pool.stream().min(Comparator.comparing(VectorDocument::price)).orElse(null);

        final BigDecimal min = minPrice != null ? minPrice : BigDecimal.ZERO;
        final BigDecimal max = maxPrice != null ? maxPrice : BigDecimal.valueOf(Long.MAX_VALUE);

        List<VectorDocument> matched = pool.stream()
                .filter(d -> d.price().compareTo(min) >= 0 && d.price().compareTo(max) <= 0)
                .collect(Collectors.toList());

        if ("UNDER".equals(filterType)) {
            matched.sort(Comparator.comparing(VectorDocument::price).reversed());
        } else {
            matched.sort(Comparator.comparing(VectorDocument::price));
        }

        VectorDocument mostAffordable = matched.stream().min(Comparator.comparing(VectorDocument::price)).orElse(null);
        VectorDocument topTier = matched.stream().max(Comparator.comparing(VectorDocument::price)).orElse(null);

        BigDecimal avg = BigDecimal.ZERO;
        if (!matched.isEmpty()) {
            BigDecimal sum = matched.stream().map(VectorDocument::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(matched.size()), 0, RoundingMode.HALF_UP);
        }

        return new PriceFilterResult(
                filterType,
                min,
                max,
                targetScope,
                matched,
                matched.size(),
                avg,
                mostAffordable,
                topTier,
                cheapestOverall
        );
    }

    private String detectTargetScope(String qLower) {
        for (VectorDocument doc : indexedDocuments) {
            String cat = doc.category().toLowerCase();
            if (qLower.contains(cat)
                    || (cat.contains("thoại") && (qLower.contains("điện thoại") || qLower.contains("đt")))
                    || (cat.contains("laptop") && (qLower.contains("laptop") || qLower.contains("máy tính")))
                    || (cat.contains("phụ kiện") && (qLower.contains("phụ kiện") || qLower.contains("tai nghe") || qLower.contains("sạc")))) {
                return doc.category();
            }
        }
        String[] brands = {"iphone", "samsung", "oppo", "xiaomi", "macbook", "ipad", "dell", "asus", "hp", "lenovo", "acer", "sony"};
        for (String b : brands) {
            if (qLower.contains(b)) {
                return Character.toUpperCase(b.charAt(0)) + b.substring(1);
            }
        }
        return "Toàn bộ cửa hàng";
    }

    private boolean matchesScope(VectorDocument doc, String scope) {
        if ("Toàn bộ cửa hàng".equalsIgnoreCase(scope)) return true;
        if (doc.category().equalsIgnoreCase(scope)) return true;
        String name = doc.name().toLowerCase();
        String s = scope.toLowerCase();
        return name.contains(s) || (doc.description() != null && doc.description().toLowerCase().contains(s));
    }

    private BigDecimal parseSinglePrice(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.toLowerCase().trim();
        if (s.contains("rưỡi")) {
            s = s.replace("rưỡi", "").trim();
            BigDecimal base = parseSinglePrice(s);
            if (base != null) return base.add(BigDecimal.valueOf(500000));
        }

        Matcher mTr = Pattern.compile("(\\d+)\\s*tr\\s*(\\d+)").matcher(s);
        if (mTr.find()) {
            double v = Double.parseDouble(mTr.group(1)) + Double.parseDouble(mTr.group(2)) / 10.0;
            return BigDecimal.valueOf((long)(v * 1_000_000));
        }

        Matcher mMillion = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:triệu|trieu|tr|m)").matcher(s);
        if (mMillion.find()) {
            double v = Double.parseDouble(mMillion.group(1).replace(",", "."));
            return BigDecimal.valueOf((long)(v * 1_000_000));
        }

        Matcher mThousand = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:k|nghìn|ngàn)").matcher(s);
        if (mThousand.find()) {
            double v = Double.parseDouble(mThousand.group(1).replace(",", "."));
            return BigDecimal.valueOf((long)(v * 1_000));
        }

        Matcher mRaw = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})+|\\d{6,})").matcher(s);
        if (mRaw.find()) {
            String clean = mRaw.group(1).replaceAll("[.,]", "");
            return new BigDecimal(clean);
        }

        return null;
    }

    private BigDecimal parseWithUnit(double val, String reference) {
        String ref = reference.toLowerCase();
        if (ref.contains("triệu") || ref.contains("trieu") || ref.contains("tr") || ref.contains("m")) {
            return BigDecimal.valueOf((long)(val * 1_000_000));
        }
        if (ref.contains("k") || ref.contains("nghìn") || ref.contains("ngàn")) {
            return BigDecimal.valueOf((long)(val * 1_000));
        }
        return BigDecimal.valueOf((long) val);
    }

    /**
     * Nhận diện và kiểm tra tình trạng tồn kho của sản phẩm
     */
    public StockCheckResult checkStock(String query) {
        if (query == null || indexedDocuments.isEmpty()) return null;
        String q = query.toLowerCase().trim();

        boolean isStockQuery = q.contains("còn hàng") || q.contains("hết hàng") || q.contains("tồn kho")
                || q.contains("còn không") || q.contains("có sẵn không") || q.contains("còn bao nhiêu")
                || q.contains("số lượng còn");

        if (!isStockQuery) return null;

        List<VectorDocument> targets = new ArrayList<>();
        for (VectorDocument doc : indexedDocuments) {
            String cleanName = doc.name().toLowerCase().replaceAll("\\(.*?\\)", "").trim();
            if (q.contains(cleanName) || isSubMatch(q, cleanName)) {
                targets.add(doc);
            }
        }

        if (targets.isEmpty()) {
            targets = similaritySearch(query, 3);
        }

        if (targets.isEmpty()) return null;

        final List<VectorDocument> finalTargets = targets;
        List<VectorDocument> inStock = targets.stream().filter(d -> d.quantity() > 0).toList();
        List<VectorDocument> outOfStock = targets.stream().filter(d -> d.quantity() <= 0).toList();

        List<VectorDocument> alternatives = new ArrayList<>();
        if (!outOfStock.isEmpty()) {
            String cat = outOfStock.get(0).category();
            alternatives = indexedDocuments.stream()
                    .filter(d -> d.category().equalsIgnoreCase(cat) && d.quantity() > 0 && !finalTargets.contains(d))
                    .limit(3)
                    .toList();
        }

        return new StockCheckResult(
                targets.get(0).name(),
                targets,
                !inStock.isEmpty(),
                inStock,
                outOfStock,
                alternatives
        );
    }

    /**
     * Nhận diện và tư vấn sản phẩm theo Nhu cầu & Mục đích sử dụng (Gaming, Học tập/Văn phòng, Chụp ảnh, Pin trâu, v.v.)
     */
    public UsageRecommendationResult findForUsage(String query) {
        if (query == null || indexedDocuments.isEmpty()) return null;
        String q = query.toLowerCase().trim();

        String intentType = null;
        String intentTitle = null;
        String adviceReason = null;

        if (q.contains("sinh viên") || q.contains("học sinh") || q.contains("học tập") || q.contains("văn phòng") || q.contains("làm việc")) {
            intentType = "STUDENT_OFFICE";
            intentTitle = "Học tập & Làm việc văn phòng";
            adviceReason = "Ưu tiên thiết bị mỏng nhẹ, pin bền, hiệu năng ổn định, bàn phím gõ êm và mức giá tối ưu ngân sách.";
        } else if (q.contains("chơi game") || q.contains("gaming") || q.contains("đồ họa") || q.contains("game") || q.contains("mạnh mẽ") || q.contains("fps")) {
            intentType = "GAMING_PERFORMANCE";
            intentTitle = "Chiến Game & Hiệu năng cao";
            adviceReason = "Ưu tiên vi xử lý hàng đầu (Apple A-Series/Snapdragon 8/Intel Core i7), RAM từ 16GB, màn hình mượt mà và tản nhiệt tốt.";
        } else if (q.contains("chụp ảnh") || q.contains("quay video") || q.contains("sống ảo") || q.contains("camera") || q.contains("selfie") || q.contains("tiktok")) {
            intentType = "CAMERA_PHOTO";
            intentTitle = "Nhiếp ảnh sắc nét & Sáng tạo nội dung";
            adviceReason = "Ưu tiên cụm camera độ phân giải cao, hỗ trợ chống rung quang học OIS, quay 4K/4K HDR và màu sắc hiển thị chuẩn xác.";
        } else if (q.contains("pin trâu") || q.contains("pin khủng") || q.contains("dùng cả ngày") || q.contains("pin lâu")) {
            intentType = "BATTERY_LONG";
            intentTitle = "Thời lượng Pin bền bỉ";
            adviceReason = "Ưu tiên dung lượng pin lớn (5000mAh+ hoặc chip tối ưu tiết kiệm điện năng) đáp ứng sử dụng liên tục cả ngày dài.";
        } else if (q.contains("nhỏ gọn") || q.contains("mỏng nhẹ") || q.contains("dễ mang") || q.contains("bỏ túi")) {
            intentType = "COMPACT";
            intentTitle = "Thiết kế Mỏng nhẹ & Nhỏ gọn";
            adviceReason = "Ưu tiên màn hình vừa vặn 6.1 inch hoặc laptop mỏng nhẹ dưới 1.4kg dễ dàng mang theo mọi nơi.";
        }

        if (intentType == null) return null;

        final String type = intentType;
        List<VectorDocument> ranked = indexedDocuments.stream()
                .sorted((d1, d2) -> Double.compare(scoreUsage(d2, type), scoreUsage(d1, type)))
                .limit(4)
                .toList();

        return new UsageRecommendationResult(
                intentType,
                intentTitle,
                ranked,
                adviceReason
        );
    }

    private double scoreUsage(VectorDocument doc, String intentType) {
        double score = 0.0;
        String text = (doc.name() + " " + (doc.description() != null ? doc.description() : "")).toLowerCase();
        String cat = doc.category().toLowerCase();

        switch (intentType) {
            case "STUDENT_OFFICE" -> {
                if (cat.contains("laptop")) score += 3.0;
                if (doc.price().compareTo(BigDecimal.valueOf(25_000_000L)) <= 0) score += 2.0;
                if (text.contains("nhẹ") || text.contains("văn phòng") || text.contains("core i5") || text.contains("8gb")) score += 2.0;
            }
            case "GAMING_PERFORMANCE" -> {
                if (text.contains("pro max") || text.contains("ultra") || text.contains("gaming") || text.contains("core i7") || text.contains("snapdragon 8")) score += 4.0;
                if (doc.price().compareTo(BigDecimal.valueOf(20_000_000L)) >= 0) score += 2.0;
                if (text.contains("16gb") || text.contains("32gb") || text.contains("512gb") || text.contains("1tb")) score += 2.0;
            }
            case "CAMERA_PHOTO" -> {
                if (text.contains("pro") || text.contains("ultra") || text.contains("camera") || text.contains("chống rung") || text.contains("ois")) score += 4.0;
                if (text.contains("200mp") || text.contains("108mp") || text.contains("50mp") || text.contains("tele")) score += 3.0;
            }
            case "BATTERY_LONG" -> {
                if (text.contains("plus") || text.contains("max") || text.contains("5000mah") || text.contains("pin trâu")) score += 4.0;
                if (text.contains("tiết kiệm pin")) score += 2.0;
            }
            case "COMPACT" -> {
                if (text.contains("mini") || text.contains("nhỏ gọn") || text.contains("6.1 inch") || text.contains("mỏng nhẹ")) score += 4.0;
                if (!text.contains("max") && !text.contains("plus") && !text.contains("ultra")) score += 1.5;
            }
        }
        return score;
    }
}
