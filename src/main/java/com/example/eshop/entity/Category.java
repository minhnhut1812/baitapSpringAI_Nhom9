package com.example.eshop.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity    // xác định đối tượng để ORM (Object Relational-Mapping)
@Table(name = "category")   // xác định tên bảng sẽ ánh xạ
@Getter @Setter @NoArgsConstructor     // lombok
public class Category {
    @Id           // đánh dấu ID
    @GeneratedValue(strategy = GenerationType.IDENTITY)       // chỉ trong SQL: sinh tự động key, thường là auto-increment
    private Long id;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tối đa 100 ký tự")
    @Column(nullable = false, unique = true, length = 100)  // để cấu hình mapping cột cụ thể
    private String name;

    @Size(max = 255, message = "Tối đa 255 ký tự")
    private String description;
}