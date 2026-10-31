package com.example.eshop.repository;

import com.example.eshop.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderByOrderDateDesc();
    List<Order> findByUserUsernameOrderByOrderDateDesc(String username);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.details d LEFT JOIN FETCH d.product ORDER BY o.orderDate DESC")
    List<Order> findAllWithDetailsByOrderDateDesc();

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.details d LEFT JOIN FETCH d.product WHERE o.user.username = :username ORDER BY o.orderDate DESC")
    List<Order> findWithDetailsByUsername(@Param("username") String username);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.details d LEFT JOIN FETCH d.product WHERE o.id = :id AND o.user.username = :username")
    Optional<Order> findByIdAndUsernameWithDetails(@Param("id") Long id, @Param("username") String username);
}
