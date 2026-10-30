package com.example.eshop.controller;

import java.security.Principal;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.eshop.entity.Order;
import com.example.eshop.service.OrderService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@GetMapping("/my")
	public String myOrders(Principal principal, Model model) {
		model.addAttribute("orders", orderService.findByUsername(principal.getName()));
		return "order/my-orders";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Principal principal, Model model) {
		model.addAttribute("order", getOwnedOrder(id, principal));
		return "order/detail";
	}

	@GetMapping("/{id}/payment")
	public String payment(@PathVariable Long id, Principal principal, Model model) {
		model.addAttribute("order", getOwnedOrder(id, principal));
		return "order/payment";
	}

	@PostMapping("/{id}/pay")
	public String pay(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
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