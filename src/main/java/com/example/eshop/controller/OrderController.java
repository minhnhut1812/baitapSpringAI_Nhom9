package com.example.eshop.controller;

import com.example.eshop.entity.Order;
import com.example.eshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping({"", "/", "/my"})
    public String myOrders(Principal principal, Model model) {
        if (principal == null || principal.getName() == null) {
            return "redirect:/login";
        }
        model.addAttribute("orders", orderService.findByUsername(principal.getName()));
        return "order/my-orders";
    }

    @GetMapping("/{id:[0-9]+}")
    public String detail(@PathVariable("id") Long id, Principal principal, Model model) {
        if (principal == null || principal.getName() == null) {
            return "redirect:/login";
        }
        model.addAttribute("order", getOwnedOrder(id, principal));
        return "order/detail";
    }

    @GetMapping("/{id:[0-9]+}/payment")
    public String payment(@PathVariable("id") Long id, Principal principal, Model model) {
        if (principal == null || principal.getName() == null) {
            return "redirect:/login";
        }
        model.addAttribute("order", getOwnedOrder(id, principal));
        return "order/payment";
    }

    @PostMapping("/{id:[0-9]+}/pay")
    public String pay(@PathVariable("id") Long id, Principal principal, RedirectAttributes ra) {
        if (principal == null || principal.getName() == null) {
            return "redirect:/login";
        }
        getOwnedOrder(id, principal);
        orderService.markPaid(id);
        ra.addFlashAttribute("message", "Thanh toán thành công đơn #" + id);
        return "redirect:/orders/" + id;
    }

    private Order getOwnedOrder(Long id, Principal principal) {
        Order o = orderService.findById(id);
        if (o.getUser() == null || !o.getUser().getUsername().equals(principal.getName())) {
            throw new AccessDeniedException("Đơn hàng không thuộc về bạn");
        }
        return o;
    }
}
