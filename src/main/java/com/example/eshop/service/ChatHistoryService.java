package com.example.eshop.service;

import com.example.eshop.ai.ChatController;
import com.example.eshop.entity.ChatMessage;
import com.example.eshop.entity.ChatSession;
import com.example.eshop.entity.User;
import com.example.eshop.repository.ChatMessageRepository;
import com.example.eshop.repository.ChatSessionRepository;
import com.example.eshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    public record ChatSessionDto(String id, String title, String updatedAt, int messageCount) {}
    public record ChatMessageDto(Long id, String role, String content, String createdAt) {}

    @Transactional(readOnly = true)
    public List<ChatSessionDto> getSessions(String username) {
        if (username == null || username.isBlank() || "guest".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            return List.of();
        }
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return List.of();

        return chatSessionRepository.findByUserOrderByUpdatedAtDesc(userOpt.get()).stream()
                .map(s -> new ChatSessionDto(
                        s.getId(),
                        s.getTitle(),
                        s.getUpdatedAt() != null ? s.getUpdatedAt().format(FORMATTER) : "",
                        s.getMessages() != null ? s.getMessages().size() : 0
                ))
                .toList();
    }

    @Transactional
    public ChatSessionDto createSession(String username, String title) {
        User user = findUser(username);
        String sessionTitle = (title != null && !title.isBlank()) ? title.trim() : "Cuộc trò chuyện mới";

        ChatSession session = new ChatSession();
        session.setId("sess_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6));
        session.setUser(user);
        session.setTitle(sessionTitle);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        ChatSession saved = chatSessionRepository.save(session);
        return new ChatSessionDto(saved.getId(), saved.getTitle(), saved.getUpdatedAt().format(FORMATTER), 0);
    }

    @Transactional
    public ChatSession getOrCreateSession(String username, String sessionId) {
        User user = findUser(username);
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> opt = chatSessionRepository.findByIdAndUser(sessionId, user);
            if (opt.isPresent()) {
                return opt.get();
            }
            Optional<ChatSession> byId = chatSessionRepository.findById(sessionId);
            if (byId.isPresent() && (byId.get().getUser() == null || byId.get().getUser().getId().equals(user.getId()))) {
                return byId.get();
            }
        }
        ChatSession session = new ChatSession();
        session.setId("sess_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6));
        session.setUser(user);
        session.setTitle("Cuộc trò chuyện mới");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(String username, String sessionId) {
        User user = findUser(username);
        if (user == null || sessionId == null) return List.of();

        Optional<ChatSession> opt = chatSessionRepository.findByIdAndUser(sessionId, user);
        if (opt.isEmpty()) {
            Optional<ChatSession> byId = chatSessionRepository.findById(sessionId);
            if (byId.isPresent() && (byId.get().getUser() == null || byId.get().getUser().getId().equals(user.getId()))) {
                opt = byId;
            } else {
                return List.of();
            }
        }

        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(opt.get()).stream()
                .map(m -> new ChatMessageDto(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt() != null ? m.getCreatedAt().format(FORMATTER) : ""
                ))
                .toList();
    }

    @Transactional
    public void saveMessage(ChatSession session, String role, String content) {
        if (session == null || content == null) return;

        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);

        session.setUpdatedAt(LocalDateTime.now());
        // Tự động cập nhật tiêu đề theo tin nhắn đầu tiên của user
        if ("user".equalsIgnoreCase(role) && ("Cuộc trò chuyện mới".equals(session.getTitle()) || session.getTitle().isBlank())) {
            String clean = content.trim();
            session.setTitle(clean.length() > 32 ? clean.substring(0, 32) + "..." : clean);
        }
        chatSessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(String username, String sessionId) {
        User user = findUser(username);
        if (user == null || sessionId == null) return;
        Optional<ChatSession> opt = chatSessionRepository.findByIdAndUser(sessionId, user);
        if (opt.isPresent()) {
            chatMessageRepository.deleteAllBySession(opt.get());
            chatSessionRepository.delete(opt.get());
        }
    }

    @Transactional
    public void clearAllSessions(String username) {
        User user = findUser(username);
        if (user == null) return;
        List<ChatSession> list = chatSessionRepository.findByUserOrderByUpdatedAtDesc(user);
        for (ChatSession s : list) {
            chatMessageRepository.deleteAllBySession(s);
        }
        chatSessionRepository.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public List<ChatController.MessageItem> getRecentHistory(ChatSession session, int limit) {
        if (session == null) return Collections.emptyList();
        List<ChatMessage> list = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        if (list.isEmpty()) return Collections.emptyList();

        int start = Math.max(0, list.size() - limit);
        return list.subList(start, list.size()).stream()
                .map(m -> new ChatController.MessageItem(m.getRole(), m.getContent()))
                .toList();
    }

    private User findUser(String username) {
        if (username == null || username.isBlank() || "guest".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
