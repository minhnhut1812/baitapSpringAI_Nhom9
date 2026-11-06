package com.example.eshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;

@SpringBootApplication
public class EshopApplication {

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        loadEnvFile();
        SpringApplication.run(EshopApplication.class, args);
    }

    private static void loadEnvFile() {
        try {
            File envFile = new File(".env");
            if (!envFile.exists()) {
                envFile = new File(System.getProperty("user.dir"), ".env");
            }
            if (envFile.exists()) {
                for (String line : Files.readAllLines(envFile.toPath())) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int eqIdx = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eqIdx).trim();
                    String val = trimmed.substring(eqIdx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1).trim();
                    }
                    System.setProperty(key, val);
                    if ("GEMINI_API_KEY".equalsIgnoreCase(key)) {
                        System.setProperty("spring.ai.google.genai.api-key", val);
                    }
                }
                System.out.println(">>> [eShop .env] Đã nạp thành công cấu hình từ file .env: " + envFile.getAbsolutePath());
            } else {
                System.err.println(">>> [eShop .env] CẢNH BÁO: Không tìm thấy file .env tại " + envFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println(">>> [eShop .env] Lỗi khi nạp file .env: " + e.getMessage());
        }
    }

}
