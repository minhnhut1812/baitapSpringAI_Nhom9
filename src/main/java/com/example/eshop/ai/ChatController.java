package com.example.eshop.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý bán hàng của cửa hàng trực tuyến eShop. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện.
            Chỉ tư vấn dựa trên dữ liệu lấy từ công cụ searchProducts/getProduct, không bịa sản phẩm, giá hay tồn kho.
            Khi giới thiệu sản phẩm, nêu tên, giá (VNĐ) và đường dẫn dạng markdown [tên](/products/id).
            Nếu hết hàng (quantity = 0) hãy nói rõ. Từ chối lịch sự các câu hỏi không liên quan đến mua sắm.
            """;

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, ProductTools productTools) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).defaultTools(productTools).build();
    }

    public record ChatRequest(@NotBlank @Size(max = 500) String message) {}

    @PostMapping
    public Map<String, String> chat(@Valid @RequestBody ChatRequest req) {
        try {
            String answer = chatClient.prompt().user(req.message()).call().content();
            return Map.of("reply", answer == null ? "" : answer);
        } catch (Exception e) {
            log.warn("Chat AI lỗi", e);
            return Map.of("reply", "Xin lỗi, trợ lý AI đang không khả dụng. Vui lòng thử lại sau.");
        }
    }
}
