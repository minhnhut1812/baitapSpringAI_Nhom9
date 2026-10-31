package com.example.eshop.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ShopAssistant assistant;

    public record ChatRequest(@NotBlank @Size(max = 500) String message) {}

    @PostMapping
    public Map<String, String> chat(@Valid @RequestBody ChatRequest req) {
        try {
            return Map.of("reply", assistant.ask(req.message()));
        } catch (Exception e) {
            log.warn("Chat AI lỗi", e);
            return Map.of("reply", "Xin lỗi, trợ lý AI đang không khả dụng. Vui lòng thử lại sau.");
        }
    }
}
