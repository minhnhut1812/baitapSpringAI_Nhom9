package com.example.eshop.controller.admin;

import com.example.eshop.service.SalesAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {
    private static final Logger log = LoggerFactory.getLogger(AdminStatisticsController.class);
    private static final String VIEW = "admin/statistics";

    private final SalesAnalyticsService salesAnalyticsService;

    @GetMapping
    public String statistics(Model model) {
        model.addAttribute("statistics", salesAnalyticsService.getDashboardStatistics());
        model.addAttribute("question", "");
        return VIEW;
    }

    @PostMapping("/ask")
    public String ask(@RequestParam(name = "question", required = false) String question, Model model) {
        model.addAttribute("statistics", salesAnalyticsService.getDashboardStatistics());
        String cleanQuestion = question == null ? "" : question.trim();
        model.addAttribute("question", cleanQuestion);
        if (cleanQuestion.isBlank()) {
            model.addAttribute("analysisError", "Vui lòng nhập câu hỏi thống kê.");
            return VIEW;
        }

        try {
            model.addAttribute("aiAnswer", salesAnalyticsService.answerQuestion(cleanQuestion));
        } catch (RuntimeException e) {
            log.error("Không thể tạo phân tích thống kê bằng AI", e);
            model.addAttribute("analysisError", "AI chưa thể phân tích lúc này. Vui lòng thử lại sau.");
        }
        return VIEW;
    }
}
