package com.example.eshop.repository;

import com.example.eshop.entity.ChatMessage;
import com.example.eshop.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);
    void deleteAllBySession(ChatSession session);
}
