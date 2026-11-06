package com.example.eshop.api;
import com.example.eshop.entity.Product;
import com.example.eshop.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiController {
 private final ProductService productService;

 public record ProductDto(Long id, String name, String description, BigDecimal price,
 Integer quantity, String imageUrl, Long categoryId, String
categoryName) {
 static ProductDto from(Product p) {
 return new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
 p.getQuantity(), p.getImageUrl(), p.getCategory().getId(),
p.getCategory().getName());
 }
 }
 public record ProductRequest(@NotBlank String name, String description,
 @NotNull @DecimalMin("0") BigDecimal price,
@NotNull @Min(0) Integer quantity,
String imageUrl, @NotNull Long categoryId) {}
 @GetMapping
 public Page<ProductDto> list(@RequestParam(required = false) String keyword,
 @RequestParam(required = false) Long categoryId,
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "10") int size) {
 return productService.search(keyword, categoryId, page, size).map(ProductDto::from);
 }
 @GetMapping("/{id}")
 public ProductDto get(@PathVariable Long id) {
 return ProductDto.from(productService.findById(id));
 }
 @PostMapping
 @ResponseStatus(HttpStatus.CREATED)
 public ProductDto create(@Valid @RequestBody ProductRequest req) {
 Product p = new Product();
 apply(req, p);
 return ProductDto.from(productService.save(p, req.categoryId()));
 }
 @PutMapping("/{id}")
 public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
 Product p = productService.findById(id);
 apply(req, p);
 return ProductDto.from(productService.save(p, req.categoryId()));
 }
 @DeleteMapping("/{id}")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable Long id) {
 productService.delete(id);
 }
 private void apply(ProductRequest r, Product p) {
 p.setName(r.name());
 p.setDescription(r.description());
 p.setPrice(r.price());
 p.setQuantity(r.quantity());
 p.setImageUrl(r.imageUrl());
 }
}