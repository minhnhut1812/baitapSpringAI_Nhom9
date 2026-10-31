package com.example.eshop.repository;

import com.example.eshop.entity.ChatSession;
import com.example.eshop.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByUserOrderByUpdatedAtDesc(User user);
    Optional<ChatSession> findByIdAndUser(String id, User user);
    void deleteByUser(User user);
}
