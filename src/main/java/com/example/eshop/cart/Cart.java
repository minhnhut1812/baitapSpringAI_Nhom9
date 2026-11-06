package com.example.eshop.cart;

import com.example.eshop.entity.Product;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

@Component
@SessionScope
public class Cart implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<Long, CartItem> items = new LinkedHashMap<>();

    private String voucherCode;
    private int discountPercent = 0;
    private BigDecimal discountFlat = BigDecimal.ZERO;

    public void add(Product p, int quantity) {
        CartItem item = items.get(p.getId());
        if (item == null) {
            items.put(p.getId(), new CartItem(p.getId(), p.getName(), p.getImageUrl(), p.getPrice(), quantity));
        } else {
            item.setQuantity(item.getQuantity() + quantity);
        }
    }

    public void update(Long productId, int quantity) {
        if (quantity <= 0) items.remove(productId);
        else if (items.containsKey(productId)) items.get(productId).setQuantity(quantity);
    }

    public void remove(Long productId) { items.remove(productId); }
    public void clear() {
        items.clear();
        removeVoucher();
    }
    public Collection<CartItem> getItems() { return items.values(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public int getQuantityOf(Long productId) {
        CartItem i = items.get(productId);
        return i == null ? 0 : i.getQuantity();
    }
    public int getTotalQuantity() {
        return items.values().stream().mapToInt(CartItem::getQuantity).sum();
    }

    public BigDecimal getSubtotal() {
        return items.values().stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void applyVoucher(String code, int percent, BigDecimal flat) {
        this.voucherCode = code;
        this.discountPercent = Math.max(0, percent);
        this.discountFlat = flat != null ? flat : BigDecimal.ZERO;
    }

    public void removeVoucher() {
        this.voucherCode = null;
        this.discountPercent = 0;
        this.discountFlat = BigDecimal.ZERO;
    }

    public String getVoucherCode() { return voucherCode; }
    public int getDiscountPercent() { return discountPercent; }
    public BigDecimal getDiscountFlat() { return discountFlat; }

    public BigDecimal getDiscountAmount() {
        BigDecimal subtotal = getSubtotal();
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        if (discountPercent > 0) {
            discount = discount.add(subtotal.multiply(BigDecimal.valueOf(discountPercent)).divide(BigDecimal.valueOf(100)));
        }
        if (discountFlat != null && discountFlat.compareTo(BigDecimal.ZERO) > 0) {
            discount = discount.add(discountFlat);
        }
        if (discount.compareTo(subtotal) > 0) {
            return subtotal;
        }
        return discount;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal subtotal = getSubtotal();
        BigDecimal discount = getDiscountAmount();
        BigDecimal total = subtotal.subtract(discount);
        return total.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : total;
    }
}
