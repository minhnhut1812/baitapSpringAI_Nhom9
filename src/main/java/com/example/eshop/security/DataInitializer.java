package com.example.eshop.security;

import com.example.eshop.entity.Role;
import com.example.eshop.entity.User;
import com.example.eshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        initChatTables();
        initUser("seller1", "Người Bán", "seller1@eshop.com", Role.ROLE_SELLER);
        initUser("customer1", "Khách Hàng 1", "customer1@eshop.com", Role.ROLE_CUSTOMER);
    }

    private void initChatTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id VARCHAR(50) PRIMARY KEY,
                    user_id BIGINT NULL,
                    title VARCHAR(255) NOT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id VARCHAR(50) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    content TEXT NOT NULL,
                    created_at DATETIME NOT NULL,
                    CONSTRAINT fk_chat_msg_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
            log.info("Đã đồng bộ cấu trúc bảng chat_sessions và chat_messages trong cơ sở dữ liệu");
        } catch (Exception e) {
            log.warn("Kiểm tra bảng chat trong DB: {}", e.getMessage());
        }
    }

    private void initUser(String username, String fullName, String email, Role role) {
        User u = userRepository.findByUsername(username).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setEmail(email);
            return newUser;
        });

        u.setFullName(fullName);
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            u.setEmail(email);
        }
        u.setRole(role);
        u.setEnabled(true);
        u.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(u);
        log.info("Đã đồng bộ tài khoản demo {} với quyền {}", username, role);
    }
}
