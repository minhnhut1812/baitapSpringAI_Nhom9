package com.example.eshop.ai;

import com.example.eshop.ai.order.OrderAiService;
import com.example.eshop.ai.vector.ProductVectorStore;
import com.example.eshop.ai.vector.VectorDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.*;

/**
 * Trợ lý AI eShop ứng dụng Vector Database & RAG (Retrieval-Augmented Generation):
 * 1) So sánh đa chiều (Giá bán, Chênh lệch giá, Chất lượng hoàn thiện, Kích thước màn hình, Cấu hình phần cứng);
 * 2) Phân tích và truy vấn Cái nhất trong các phân loại (Đắt nhất, Rẻ nhất, Kích thước to nhất, Cấu hình mạnh nhất);
 * 3) Tìm kiếm tương đồng ngữ nghĩa (Semantic Vector Search) qua Cosine Similarity.
 */
@Service
public class ShopAssistant {
    static final String OFF_TOPIC = "Mình là trợ lý bán hàng eShop, chỉ hỗ trợ tư vấn, so sánh giá và thông số các sản phẩm của cửa hàng thôi ạ. Bạn muốn tìm hiểu sản phẩm nào?";
    static final String NO_DATA = "Hiện eShop chưa có sản phẩm nào phù hợp với câu hỏi của bạn. Bạn thử mô tả thêm nhu cầu nhé!";

    private static final String COMPARISON_SYSTEM_PROMPT = """
            Bạn là chuyên gia tư vấn công nghệ và bán hàng của eShop.
            Nhiệm vụ của bạn là phân tích và SO SÁNH chi tiết các sản phẩm theo yêu cầu của khách hàng dựa HOÀN TOÀN vào dữ liệu được cung cấp từ Vector Database.

            Yêu cầu định dạng & nội dung trả lời:
            1. So sánh Giá bán:
               - Nêu rõ giá bán chính xác của từng sản phẩm.
               - Chỉ rõ sản phẩm nào đắt hơn / rẻ hơn và tính mức chênh lệch giá cụ thể (bằng VNĐ).
            2. So sánh Kích thước & Thiết kế:
               - So sánh kích thước màn hình (inch), không gian hiển thị, độ gọn nhẹ và cảm giác cầm nắm.
            3. So sánh Chất lượng & Cấu hình:
               - So sánh vật liệu hoàn thiện (Titan, kim loại nguyên khối, kính cường lực...).
               - So sánh vi xử lý (chip), RAM/bộ nhớ, camera, dung lượng pin và độ bền.
            4. Bảng so sánh trực quan (Markdown Table):
               - Lập bảng Markdown so sánh ngắn gọn các tiêu chí: Giá bán, Kích thước màn hình, Cấu hình/Chip, Ưu điểm chính.
            5. Đánh giá & Lời khuyên chọn mua:
               - Nêu rõ ai nên mua sản phẩm nào (người thích cấu hình mạnh, người cần máy nhỏ gọn, người ưu tiên tiết kiệm ngân sách...).
            6. Luôn đính kèm link dạng markdown [Tên sản phẩm](/products/id) cho mỗi sản phẩm. Nếu số lượng = 0 thì thông báo là tạm hết hàng.
            """;

    private static final String EXTREMES_SYSTEM_PROMPT = """
            Bạn là trợ lý bán hàng của eShop.
            Nhiệm vụ của bạn là trả lời câu hỏi của khách hàng về "CÁI NHẤT" trong các phân loại (sản phẩm rẻ nhất, đắt nhất, màn hình to nhất, cấu hình mạnh nhất...) dựa trên dữ liệu thực tế từ Vector Database.

            Yêu cầu nội dung:
            1. Trả lời trực tiếp và rõ ràng sản phẩm đạt danh hiệu "nhất" được hỏi, kèm giá bán (VNĐ) và link markdown [Tên sản phẩm](/products/id).
            2. Nêu bật các điểm vượt trội của sản phẩm đó (giá hấp dẫn nhất, hoặc cấu hình/chất lượng/màn hình hàng đầu).
            3. Cung cấp bối cảnh toàn diện trong danh mục: Nêu sản phẩm rẻ nhất và đắt nhất cùng mức giá trung bình của danh mục đó để khách hàng đối chiếu.
            4. Giới thiệu thêm 1-2 sản phẩm tiệm cận đáng chú ý khác trong cùng phân loại để khách hàng có thêm sự lựa chọn.
            5. Tuyệt đối không bịa sản phẩm, giá hay thông số ngoài dữ liệu được cung cấp.
            """;

    private static final String PRICE_FILTER_SYSTEM_PROMPT = """
            Bạn là chuyên gia tư vấn mua sắm công nghệ và bán hàng tận tâm của eShop.
            Nhiệm vụ của bạn là trình bày và tư vấn danh sách sản phẩm ĐÁP ỨNG CHÍNH XÁC YÊU CẦU MỨC GIÁ của khách hàng (dưới giá, trên giá, trong khoảng giá, tầm giá) dựa hoàn toàn vào dữ liệu Vector Database.

            Yêu cầu định dạng & nội dung trả lời:
            1. Lời mở đầu thân thiện:
               - Nêu rõ điều kiện giá được hỏi (vd: "Dưới 10.000.000 VNĐ", "Từ 5.000.000 đến 15.000.000 VNĐ", "Trên 20.000.000 VNĐ") và tổng số lượng sản phẩm phù hợp hiện có tại cửa hàng.
            2. Bảng danh sách sản phẩm trực quan (Markdown Table):
               - Lập bảng gồm các cột: | Sản phẩm | Danh mục | Giá bán (VNĐ) | Tồn kho | Điểm nổi bật |
               - Tên sản phẩm BẮT BUỘC có link markdown: [Tên sản phẩm](/products/id)
               - Cột Tồn kho: Nếu > 0 ghi "Còn hàng (X cái)", nếu = 0 ghi "Tạm hết hàng".
            3. Phân tích & Gợi ý Top Picks theo ngân sách:
               - Chỉ rõ "Lựa chọn tiết kiệm nhất" (sản phẩm có giá mềm nhất trong nhóm).
               - Chỉ rõ "Lựa chọn cấu hình mạnh nhất" (sản phẩm cao cấp nhất tiệm cận ngân sách của khách).
               - Nhận xét ngắn gọn về màn hình, chip, camera hoặc dung lượng pin của các sản phẩm nổi bật.
            4. Hướng dẫn đặt hàng:
               - Hướng dẫn khách hàng nhấn trực tiếp vào link tên sản phẩm để xem chi tiết và bấm "Thêm vào giỏ hàng" hoặc "Mua ngay".
            5. Nếu không có sản phẩm nào thỏa mãn mức giá:
               - Thông báo rõ ràng và chân thành.
               - Gợi ý sản phẩm có giá thấp nhất hiện có tại cửa hàng hoặc phân loại tương ứng để khách hàng tham khảo.
            """;

    private static final String STOCK_SYSTEM_PROMPT = """
            Bạn là trợ lý bán hàng của eShop.
            Nhiệm vụ của bạn là giải đáp câu hỏi của khách hàng về TÌNH TRẠNG CÒN HÀNG / TỒN KHO của sản phẩm một cách chính xác, minh bạch.

            Yêu cầu nội dung:
            1. Báo cáo rõ ràng tình trạng tồn kho của từng sản phẩm được hỏi kèm link markdown [Tên sản phẩm](/products/id) và giá bán (VNĐ).
            2. Nếu sản phẩm còn hàng (tồn kho > 0):
               - Thông báo số lượng còn lại cụ thể trong kho.
               - Nhắc khách hàng có thể bấm vào link sản phẩm để Thêm vào giỏ hàng hoặc Mua ngay kẻo hết.
            3. Nếu sản phẩm tạm hết hàng (tồn kho = 0):
               - Thông báo chân thành sản phẩm đang tạm hết hàng và eShop đang liên hệ nhập thêm.
               - ĐỀ XUẤT NGAY 1-3 sản phẩm thay thế cùng phân loại đang CÒN HÀNG kèm giá và link chi tiết.
            """;

    private static final String USAGE_SYSTEM_PROMPT = """
            Bạn là chuyên gia tư vấn công nghệ của eShop.
            Nhiệm vụ của bạn là tư vấn chọn mua sản phẩm tối ưu nhất theo ĐÚNG NHU CẦU & MỤC ĐÍCH SỬ DỤNG của khách hàng (Sinh viên/Văn phòng, Chơi game/Đồ họa, Chụp ảnh/Quay video, Pin trâu, Thiết kế nhỏ gọn).

            Yêu cầu nội dung:
            1. Nhấn mạnh tiêu chí quan trọng nhất cho nhu cầu được hỏi (ví dụ: sinh viên cần mỏng nhẹ pin trâu giá mềm; game thủ cần chip mạnh tản nhiệt tốt màn hình mượt).
            2. Trình bày danh sách 2-4 sản phẩm phù hợp nhất kèm link markdown [Tên sản phẩm](/products/id), giá bán (VNĐ), thông số kỹ thuật then chốt và lý do vì sao sản phẩm đó phù hợp với nhu cầu.
            3. Xếp hạng hoặc chỉ ra "Sự lựa chọn số 1" đáng mua nhất.
            4. Hướng dẫn khách hàng bấm vào link để đặt hàng hoặc thêm vào giỏ.
            """;

    private static final String STORE_POLICY_PROMPT = """
            Bạn là nhân viên chăm sóc khách hàng của eShop. Trả lời thân thiện, nhiệt tình và chuyên nghiệp về các CHÍNH SÁCH BÁN HÀNG & DỊCH VỤ của eShop:

            Thông tin chính sách chuẩn của eShop:
            1. Chính sách Bảo hành:
               - Tất cả sản phẩm điện thoại, laptop, phụ kiện đều được bảo hành chính hãng 12 tháng.
               - Đổi mới 1 đổi 1 trong vòng 30 ngày đầu tiên nếu phát sinh lỗi kỹ thuật từ nhà sản xuất.
            2. Chính sách Giao hàng & Vận chuyển:
               - Giao hàng toàn quốc trên 63 tỉnh thành.
               - MIỄN PHÍ VẬN CHUYỂN cho tất cả đơn hàng từ 500.000 VNĐ trở lên.
               - Thời gian giao hàng: Nội thành 1 - 2 ngày làm việc; Ngoại thành / Tỉnh 2 - 4 ngày.
               - Khách hàng được quyền kiểm tra hàng (đồng kiểm) trước khi thanh toán.
            3. Phương thức Thanh toán:
               - Hỗ trợ Thanh toán khi nhận hàng (COD - nhận hàng trả tiền mặt).
               - Hỗ trợ Thanh toán trực tuyến qua Chuyển khoản ngân hàng hoặc Thẻ.
            4. Hướng dẫn Mua hàng & Giỏ hàng:
               - Bước 1: Khách hàng click vào tên sản phẩm [Tên sản phẩm](/products/id).
               - Bước 2: Bấm nút "Thêm vào giỏ hàng" hoặc "Mua ngay".
               - Bước 3: Vào biểu tượng Giỏ hàng trên thanh menu (hoặc truy cập [/cart](/cart)) để kiểm tra số lượng và bấm "Tiến hành thanh toán".
               - Khách hàng có thể đăng nhập tài khoản để lưu lại lịch sử đơn hàng và theo dõi hành trình giao nhận.
            """;

    private static final String ORDER_LOOKUP_SYSTEM_PROMPT = """
            Bạn là trợ lý bán hàng và chăm sóc khách hàng chuyên nghiệp của eShop.
            Nhiệm vụ của bạn là tra cứu và giải đáp thông tin ĐƠN HÀNG VÀ THỐNG KÊ CHI TIÊU của khách hàng một cách chi tiết, minh bạch, bảo mật và chính xác 100% dựa trên dữ liệu được cung cấp.

            QUY TẮC BẮT BUỘC VỀ ĐƯỜNG DẪN LINK:
            - Luôn sử dụng SỐ ID THỰC TẾ của đơn hàng từ dữ liệu (ví dụ: nếu mã đơn là 1 thì viết [Xem đơn #1](/orders/1), nếu mã đơn là 5 thì viết [Xem đơn #5](/orders/5)).
            - TUYỆT ĐỐI KHÔNG ĐƯỢC viết chữ 'id' hay '#id' trong link (KHÔNG viết [Xem đơn #id](/orders/id)). Phải dùng số thực tế từ dữ liệu!

            Yêu cầu định dạng & nội dung trả lời:
            1. Khi khách hỏi THỐNG KÊ MUA SẮM (đã mua bao nhiêu đơn, bao nhiêu tiền, đơn nào cao nhất...):
               - Báo cáo rõ ràng các chỉ số:
                 • Tổng số đơn hàng đã đặt: (nêu rõ số đơn).
                 • Tổng số tiền đã chi tiêu: (định dạng VNĐ rõ ràng).
                 • Giá trị đơn hàng trung bình.
                 • ĐƠN HÀNG CÓ GIÁ TRỊ CAO NHẤT: Nêu rõ mã đơn kèm link thực tế ví dụ [Đơn #1](/orders/1), tổng tiền VNĐ, ngày đặt, trạng thái và các sản phẩm trong đơn.
                 • Đơn hàng gần đây nhất: Nêu mã đơn kèm link thực tế ví dụ [Đơn #1](/orders/1), ngày đặt, trạng thái.
               - Lập bảng Markdown tóm tắt các đơn hàng gần đây (Cột: | Mã đơn | Ngày đặt | Tổng tiền (VNĐ) | Trạng thái | Xem chi tiết |).
               - Ở cột Mã đơn hoặc Xem chi tiết, BẮT BUỘC chèn link có số thực tế: [Xem đơn #1](/orders/1).
            2. Khi khách hỏi kiểm tra MỘT ĐƠN HÀNG CỤ THỂ (vd: đơn #12, đơn số 5):
               - Nêu rõ mã đơn kèm link số thực tế ví dụ [Xem chi tiết đơn #12](/orders/12), thời gian đặt hàng, người nhận, số điện thoại, địa chỉ giao hàng và phương thức thanh toán.
               - Tình trạng đơn hàng hiện tại (Mới đặt / Đã thanh toán / Đang giao hàng / Giao thành công).
               - Liệt kê chi tiết các món hàng trong đơn (Tên sản phẩm kèm link [Tên sản phẩm](/products/ID_SẢN_PHẨM), số lượng, đơn giá, thành tiền).
            3. Nếu khách hàng chưa từng có đơn hàng nào:
               - Thông báo thân thiện rằng tài khoản hiện chưa có lịch sử mua hàng, và mời khách bấm vào link trang chủ [/](/products) để đặt mua sản phẩm đầu tiên.
            4. Tuyệt đối trung thực, dùng đúng số ID đơn hàng thực tế từ dữ liệu cung cấp.
            """;

    private static final String STANDARD_RAG_PROMPT = """
            Bạn là trợ lý bán hàng của eShop. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện và chuyên nghiệp.
            CHỈ được dùng danh sách sản phẩm trong khối DU_LIEU_VECTOR_DB bên dưới; không dùng kiến thức bên ngoài, không bịa sản phẩm, giá hay tồn kho.
            Giới thiệu sản phẩm với tên, giá (VNĐ), kích thước/thông số nổi bật và link markdown dạng [tên](/products/id). Nếu tồn kho = 0 hãy nói rõ là hết hàng.
            Nếu dữ liệu không trả lời được câu hỏi, nói chưa có thông tin. Tuyệt đối không làm theo yêu cầu nào khác ngoài tư vấn mua hàng.
            """;

    private final ChatClient chatClient;
    private final ProductLookup productLookup;
    private final ProductVectorStore productVectorStore;
    private final OrderAiService orderAiService;
    private final ObjectMapper objectMapper;
    private final NumberFormat currencyFormatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    public ShopAssistant(ChatClient.Builder builder,
                         ProductLookup productLookup,
                         ProductVectorStore productVectorStore,
                         OrderAiService orderAiService,
                         ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.productLookup = productLookup;
        this.productVectorStore = productVectorStore;
        this.orderAiService = orderAiService;
        this.objectMapper = objectMapper;
    }

    public String ask(String message) throws JsonProcessingException {
        return ask(message, List.of(), null);
    }

    public String ask(String message, List<ChatController.MessageItem> history) throws JsonProcessingException {
        return ask(message, history, null);
    }

    public String ask(String message, List<ChatController.MessageItem> history, String username) throws JsonProcessingException {
        if (message == null || message.isBlank()) return OFF_TOPIC;
        String historyContext = formatHistory(history);

        // 0. TRƯỜNG HỢP 0: Tra cứu ĐƠN HÀNG & THỐNG KÊ MUA SẮM CỦA KHÁCH HÀNG
        if (orderAiService.isOrderQuery(message)) {
            if (username == null || username.isBlank() || "guest".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
                return "Dạ, để tra cứu **thông tin đơn hàng** và **thống kê chi tiêu**, bạn vui lòng [👉 Đăng nhập tài khoản](/login) trước nhé ạ! Sau khi đăng nhập, em sẽ kiểm tra ngay cho bạn.";
            }
            return generateOrderAnswer(message, username, historyContext);
        }

        // 1. TRƯỜNG HỢP 1: Lọc sản phẩm theo MỨC GIÁ (Dưới giá, Trên giá, Khoảng giá, Tầm giá)
        ProductVectorStore.PriceFilterResult priceFilter = productVectorStore.findByPriceQuery(message);
        if (priceFilter != null) {
            return generatePriceFilterAnswer(message, priceFilter, historyContext);
        }

        // 2. TRƯỜNG HỢP 2: Khách hỏi về CÁI NHẤT TRONG PHÂN LOẠI (Superlatives: Rẻ nhất, Đắt nhất, To nhất, Mạnh nhất)
        ProductVectorStore.ExtremesResult extremes = productVectorStore.findExtremes(message);
        if (extremes != null) {
            return generateExtremesAnswer(message, extremes, historyContext);
        }

        // 3. TRƯỜNG HỢP 3: Khách yêu cầu SO SÁNH (Giá cả, Chất lượng, Kích thước giữa các sản phẩm)
        if (isComparisonQuery(message)) {
            ProductVectorStore.ComparisonResult compResult = productVectorStore.findForComparison(message);
            if (compResult != null && compResult.products().size() >= 2) {
                return generateComparisonAnswer(message, compResult, historyContext);
            }
        }

        // 4. TRƯỜNG HỢP 4: Kiểm tra TỒN KHO & ĐẶT MUA (Còn hàng, Hết hàng, Còn bao nhiêu)
        ProductVectorStore.StockCheckResult stockCheck = productVectorStore.checkStock(message);
        if (stockCheck != null) {
            return generateStockAnswer(message, stockCheck, historyContext);
        }

        // 5. TRƯỜNG HỢP 5: Tư vấn theo NHU CẦU & MỤC ĐÍCH SỬ DỤNG (Sinh viên, Gaming, Chụp ảnh, Pin trâu, v.v.)
        ProductVectorStore.UsageRecommendationResult usageResult = productVectorStore.findForUsage(message);
        if (usageResult != null) {
            return generateUsageAnswer(message, usageResult, historyContext);
        }

        // 6. TRƯỜNG HỢP 6: Chính sách cửa hàng (Bảo hành 12 tháng, Đổi trả 30 ngày, Giao hàng, Thanh toán COD)
        if (isPolicyOrFaqQuery(message)) {
            return generatePolicyAnswer(message, historyContext);
        }

        // 7. TRƯỜNG HỢP 7: Tra cứu nhanh từ khóa sản phẩm trực tiếp (vd: "oppo", "iphone 15", "laptop")
        List<ProductLookup.ProductInfo> directProducts = productLookup.findDirect(message);
        if (!directProducts.isEmpty()) {
            return generateStandardAnswer(message, directProducts, historyContext);
        }

        // 8. TRƯỜNG HỢP 8: Tìm kiếm tương đồng qua Vector Database (Semantic Vector Search)
        List<VectorDocument> vectorDocs = productVectorStore.similaritySearch(message, 4);
        if (!vectorDocs.isEmpty()) {
            return generateVectorAnswer(message, vectorDocs, historyContext);
        }

        return NO_DATA;
    }

    private boolean isComparisonQuery(String message) {
        String lower = message.toLowerCase();
        return lower.contains("so sánh") || lower.contains("khác nhau") || lower.contains(" vs ")
                || lower.contains("với") || lower.contains("hơn") || lower.contains("kém")
                || lower.contains("nên mua") || lower.contains("chất lượng") || lower.contains("kích thước");
    }

    private boolean isPolicyOrFaqQuery(String query) {
        String lower = query.toLowerCase();
        return lower.contains("bảo hành") || lower.contains("đổi trả") || lower.contains("đổi hàng")
                || lower.contains("giao hàng") || lower.contains("vận chuyển") || lower.contains("ship")
                || lower.contains("phí giao") || lower.contains("mấy ngày nhận") || lower.contains("thanh toán")
                || lower.contains("ship cod") || lower.contains("nhận hàng trả tiền") || lower.contains("cách mua")
                || lower.contains("cách đặt") || lower.contains("hướng dẫn mua") || lower.contains("giỏ hàng ở đâu")
                || lower.contains("chính sách");
    }

    private String generatePriceFilterAnswer(String userMessage, ProductVectorStore.PriceFilterResult filter, String historyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KẾT QUẢ LỌC SẢN PHẨM THEO MỨC GIÁ TỪ VECTOR DATABASE ===\n");
        sb.append("Điều kiện lọc: ").append(filter.filterType()).append("\n");
        sb.append(String.format("Phạm vi phân loại: %s\n", filter.targetScope()));
        if ("UNDER".equals(filter.filterType())) {
            sb.append(String.format("Mức giá tối đa: Dưới %s VNĐ\n", currencyFormatter.format(filter.maxPrice())));
        } else if ("ABOVE".equals(filter.filterType())) {
            sb.append(String.format("Mức giá tối thiểu: Trên %s VNĐ\n", currencyFormatter.format(filter.minPrice())));
        } else {
            sb.append(String.format("Khoảng giá: Từ %s VNĐ đến %s VNĐ\n",
                    currencyFormatter.format(filter.minPrice()), currencyFormatter.format(filter.maxPrice())));
        }
        sb.append(String.format("Tổng số sản phẩm tìm thấy: %d\n\n", filter.totalCount()));

        if (filter.totalCount() > 0) {
            sb.append("DANH SÁCH SẢN PHẨM PHÙ HỢP:\n");
            for (VectorDocument d : filter.matchedProducts()) {
                sb.append(String.format("• [%s](%s)\n", d.name(), d.link()));
                sb.append(String.format("  - Phân loại: %s\n", d.category()));
                sb.append(String.format("  - Giá bán: %s VNĐ\n", currencyFormatter.format(d.price())));
                sb.append(String.format("  - Tình trạng: %s\n", d.quantity() > 0 ? "Còn hàng (" + d.quantity() + " cái)" : "Tạm hết hàng"));
                d.specs().forEach((k, v) -> sb.append(String.format("  - %s: %s\n", k, v)));
                sb.append("\n");
            }

            if (filter.mostAffordable() != null) {
                sb.append(String.format("LỰA CHỌN TIẾT KIỆM NHẤT: [%s](%s) với giá %s VNĐ\n",
                        filter.mostAffordable().name(), filter.mostAffordable().link(), currencyFormatter.format(filter.mostAffordable().price())));
            }
            if (filter.topTier() != null) {
                sb.append(String.format("LỰA CHỌN CẤU HÌNH MẠNH NHẤT: [%s](%s) với giá %s VNĐ\n",
                        filter.topTier().name(), filter.topTier().link(), currencyFormatter.format(filter.topTier().price())));
            }
            sb.append(String.format("Mức giá trung bình nhóm: %s VNĐ\n", currencyFormatter.format(filter.averagePrice())));
        } else {
            sb.append("KHÔNG CÓ SẢN PHẨM THỎA MÃN ĐIỀU KIỆN GIÁ NÀY.\n");
            if (filter.cheapestOverall() != null) {
                sb.append(String.format("Sản phẩm có giá thấp nhất hiện tại trong phân loại [%s] là: [%s](%s) với giá %s VNĐ (Tồn kho: %d)\n",
                        filter.targetScope(), filter.cheapestOverall().name(), filter.cheapestOverall().link(),
                        currencyFormatter.format(filter.cheapestOverall().price()), filter.cheapestOverall().quantity()));
            }
        }

        StringBuilder instruction = new StringBuilder(PRICE_FILTER_SYSTEM_PROMPT);
        instruction.append("\n\n").append(sb);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateStockAnswer(String userMessage, ProductVectorStore.StockCheckResult stock, String historyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KẾT QUẢ KIỂM TRA TỒN KHO TỪ VECTOR DATABASE ===\n");
        sb.append("Sản phẩm truy vấn: ").append(stock.queryProduct()).append("\n\n");

        if (!stock.inStockProducts().isEmpty()) {
            sb.append("CÁC SẢN PHẨM ĐANG CÒN HÀNG:\n");
            for (VectorDocument d : stock.inStockProducts()) {
                sb.append(String.format("• [%s](%s) - Giá: %s VNĐ - Số lượng còn lại: %d cái\n",
                        d.name(), d.link(), currencyFormatter.format(d.price()), d.quantity()));
            }
            sb.append("\n");
        }

        if (!stock.outOfStockProducts().isEmpty()) {
            sb.append("CÁC SẢN PHẨM TẠM HẾT HÀNG (Tồn kho = 0):\n");
            for (VectorDocument d : stock.outOfStockProducts()) {
                sb.append(String.format("• [%s](%s) - Giá: %s VNĐ - Tạm hết hàng\n",
                        d.name(), d.link(), currencyFormatter.format(d.price())));
            }
            sb.append("\n");
        }

        if (!stock.alternatives().isEmpty()) {
            sb.append("SẢN PHẨM THAY THẾ TƯƠNG ĐƯƠNG ĐANG CÒN HÀNG TẠI CỬA HÀNG:\n");
            for (VectorDocument d : stock.alternatives()) {
                sb.append(String.format("• [%s](%s) - Giá: %s VNĐ - Tồn kho: %d cái\n",
                        d.name(), d.link(), currencyFormatter.format(d.price()), d.quantity()));
            }
            sb.append("\n");
        }

        StringBuilder instruction = new StringBuilder(STOCK_SYSTEM_PROMPT);
        instruction.append("\n\n").append(sb);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateUsageAnswer(String userMessage, ProductVectorStore.UsageRecommendationResult usage, String historyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KẾT QUẢ TƯ VẤN THEO NHU CẦU TỪ VECTOR DATABASE ===\n");
        sb.append("Nhu cầu khách hàng: ").append(usage.intentTitle()).append("\n");
        sb.append("Tiêu chuẩn lựa chọn: ").append(usage.adviceReason()).append("\n\n");

        sb.append("DANH SÁCH SẢN PHẨM ĐƯỢC CHỌN LỌC PHÙ HỢP NHẤT:\n");
        for (VectorDocument d : usage.recommendedProducts()) {
            sb.append(String.format("• [%s](%s)\n", d.name(), d.link()));
            sb.append(String.format("  - Phân loại: %s\n", d.category()));
            sb.append(String.format("  - Giá bán: %s VNĐ\n", currencyFormatter.format(d.price())));
            sb.append(String.format("  - Tồn kho: %d\n", d.quantity()));
            d.specs().forEach((k, v) -> sb.append(String.format("  - %s: %s\n", k, v)));
            if (d.description() != null && !d.description().isBlank()) {
                sb.append(String.format("  - Mô tả: %s\n", d.description()));
            }
            sb.append("\n");
        }

        StringBuilder instruction = new StringBuilder(USAGE_SYSTEM_PROMPT);
        instruction.append("\n\n").append(sb);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generatePolicyAnswer(String userMessage, String historyContext) {
        StringBuilder instruction = new StringBuilder(STORE_POLICY_PROMPT);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateComparisonAnswer(String userMessage, ProductVectorStore.ComparisonResult compResult, String historyContext) {
        StringBuilder instruction = new StringBuilder(COMPARISON_SYSTEM_PROMPT);
        instruction.append("\n\n").append(compResult.comparisonData());
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateExtremesAnswer(String userMessage, ProductVectorStore.ExtremesResult extremes, String historyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DỮ LIỆU PHÂN TÍCH 'CÁI NHẤT' TỪ VECTOR DATABASE ===\n");
        sb.append("Danh mục phân tích: ").append(extremes.categoryName()).append("\n");
        sb.append("Tiêu chí truy vấn: ").append(extremes.extremeType()).append("\n\n");

        VectorDocument top = extremes.targetProduct();
        sb.append(String.format("SẢN PHẨM ĐỨNG ĐẦU TIÊU CHÍ: [%s](%s)\n", top.name(), top.link()));
        sb.append(String.format(" - Giá bán: %s VNĐ\n", currencyFormatter.format(top.price())));
        sb.append(String.format(" - Tồn kho: %d\n", top.quantity()));
        top.specs().forEach((k, v) -> sb.append(String.format(" - %s: %s\n", k, v)));
        if (top.description() != null && !top.description().isBlank()) {
            sb.append(String.format(" - Mô tả: %s\n", top.description()));
        }
        sb.append("\n");

        sb.append("BỐI CẢNH GIÁ CẢ TOÀN DANH MỤC:\n");
        sb.append(String.format(" - Sản phẩm có giá rẻ nhất: [%s](%s) (%s VNĐ)\n",
                extremes.cheapest().name(), extremes.cheapest().link(), currencyFormatter.format(extremes.cheapest().price())));
        sb.append(String.format(" - Sản phẩm có giá đắt nhất: [%s](%s) (%s VNĐ)\n",
                extremes.mostExpensive().name(), extremes.mostExpensive().link(), currencyFormatter.format(extremes.mostExpensive().price())));
        sb.append(String.format(" - Mức giá trung bình: %s VNĐ\n\n", currencyFormatter.format(extremes.averagePrice())));

        sb.append("CÁC SẢN PHẨM KHÁC TRONG DANH MỤC ĐỂ ĐỐI CHIẾU:\n");
        for (VectorDocument d : extremes.allCategoryProducts()) {
            sb.append(String.format(" • [%s](%s) - %s VNĐ (Tồn kho: %d)\n",
                    d.name(), d.link(), currencyFormatter.format(d.price()), d.quantity()));
        }

        StringBuilder instruction = new StringBuilder(EXTREMES_SYSTEM_PROMPT);
        instruction.append("\n\n").append(sb);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateOrderAnswer(String userMessage, String username, String historyContext) {
        Long specificOrderId = orderAiService.extractOrderId(userMessage);

        String orderContextData;
        if (specificOrderId != null) {
            Optional<OrderAiService.OrderDto> specificOrder = orderAiService.getSpecificOrder(username, specificOrderId);
            if (specificOrder.isPresent()) {
                orderContextData = orderAiService.buildSpecificOrderPromptData(specificOrder.get());
            } else {
                orderContextData = String.format("KẾT QUẢ TÌM KIẾM: Không tìm thấy đơn hàng #%d nào thuộc tài khoản '%s'. Khách hàng có thể kiểm tra danh sách đơn của mình tại link [/orders/my](/orders/my).",
                        specificOrderId, username);
            }
        } else {
            OrderAiService.CustomerOrderStats stats = orderAiService.getCustomerStats(username);
            orderContextData = orderAiService.buildStatsPromptData(stats);
        }

        StringBuilder instruction = new StringBuilder(ORDER_LOOKUP_SYSTEM_PROMPT);
        instruction.append("\n\n").append(orderContextData);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateVectorAnswer(String userMessage, List<VectorDocument> docs, String historyContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DANH SÁCH SẢN PHẨM PHÙ HỢP TỪ VECTOR DATABASE ===\n");
        for (VectorDocument d : docs) {
            sb.append(String.format("• [%s](%s)\n", d.name(), d.link()));
            sb.append(String.format("  - Danh mục: %s\n", d.category()));
            sb.append(String.format("  - Giá bán: %s VNĐ\n", currencyFormatter.format(d.price())));
            sb.append(String.format("  - Tồn kho: %d\n", d.quantity()));
            d.specs().forEach((k, v) -> sb.append(String.format("  - %s: %s\n", k, v)));
            if (d.description() != null && !d.description().isBlank()) {
                sb.append(String.format("  - Mô tả: %s\n", d.description()));
            }
            sb.append("\n");
        }

        StringBuilder instruction = new StringBuilder(STANDARD_RAG_PROMPT);
        instruction.append("\n\nDU_LIEU_VECTOR_DB:\n").append(sb);
        if (!historyContext.isEmpty()) {
            instruction.append("\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n").append(historyContext);
        }

        String answer = chatClient.prompt()
                .system(instruction.toString())
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String generateStandardAnswer(String userMessage, List<ProductLookup.ProductInfo> products, String historyContext)
            throws JsonProcessingException {
        String data = objectMapper.writeValueAsString(products);
        String systemInstruction = STANDARD_RAG_PROMPT + "\n\nDU_LIEU_VECTOR_DB:\n" + data;
        if (!historyContext.isEmpty()) {
            systemInstruction += "\nLỊCH SỬ HỘI THOẠI TRƯỚC ĐÓ:\n" + historyContext;
        }

        String answer = chatClient.prompt()
                .system(systemInstruction)
                .user(userMessage)
                .call()
                .content();

        return (answer == null || answer.isBlank()) ? NO_DATA : answer;
    }

    private String formatHistory(List<ChatController.MessageItem> history) {
        if (history == null || history.isEmpty()) return "";
        int start = Math.max(0, history.size() - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            var item = history.get(i);
            if (item != null && item.content() != null && !item.content().isBlank()) {
                String sender = "user".equalsIgnoreCase(item.role()) ? "Khách: " : "Trợ lý: ";
                sb.append(sender).append(item.content().trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
