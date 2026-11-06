package com.example.eshop.ai;

import com.example.eshop.cart.Cart;
import com.example.eshop.entity.ChatSession;
import com.example.eshop.service.ChatHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ShopAssistant assistant;
    private final ChatHistoryService chatHistoryService;
    private final Cart cart;

    public record MessageItem(String role, String content) {}

    public record ChatRequest(
            @NotBlank @Size(max = 500) String message,
            String sessionId,
            List<MessageItem> history
    ) {}

    public record CreateSessionRequest(String title) {}

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(Principal principal) {
        String username = getUsername(principal);
        if (username == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(chatHistoryService.getSessions(username));
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody(required = false) CreateSessionRequest req, Principal principal) {
        String username = getUsername(principal);
        if (username == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chưa đăng nhập"));
        }
        String title = (req != null) ? req.title() : null;
        return ResponseEntity.ok(chatHistoryService.createSession(username, title));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable("sessionId") String sessionId, Principal principal) {
        String username = getUsername(principal);
        if (username == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(chatHistoryService.getMessages(username, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable("sessionId") String sessionId, Principal principal) {
        String username = getUsername(principal);
        if (username != null) {
            chatHistoryService.deleteSession(username, sessionId);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<?> clearAllSessions(Principal principal) {
        String username = getUsername(principal);
        if (username != null) {
            chatHistoryService.clearAllSessions(username);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping
    public Map<String, Object> chat(@Valid @RequestBody ChatRequest req, Principal principal) {
        String username = getUsername(principal);
        Map<String, Object> resp = new HashMap<>();
        try {
            if (username != null) {
                ChatSession session = chatHistoryService.getOrCreateSession(username, req.sessionId());
                chatHistoryService.saveMessage(session, "user", req.message());

                List<MessageItem> history = chatHistoryService.getRecentHistory(session, 8);
                String reply = assistant.ask(req.message(), history, username);

                chatHistoryService.saveMessage(session, "bot", reply);

                resp.put("reply", reply);
                resp.put("sessionId", session.getId());
                resp.put("title", session.getTitle());
                resp.put("cartCount", cart.getTotalQuantity());
                return resp;
            } else {
                String reply = assistant.ask(req.message(), req.history(), null);
                resp.put("reply", reply);
                resp.put("cartCount", cart.getTotalQuantity());
                return resp;
            }
        } catch (Throwable t) {
            log.warn("Chat AI lỗi: ", t);
            resp.put("reply", "Xin lỗi, trợ lý AI đang không khả dụng. Vui lòng thử lại sau.");
            resp.put("cartCount", cart.getTotalQuantity());
            return resp;
        }
    }

    private String getUsername(Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(principal.getName())) {
            return principal.getName();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return null;
    }
}
