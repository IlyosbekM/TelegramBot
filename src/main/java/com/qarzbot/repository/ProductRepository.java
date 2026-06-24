package com.qarzbot.repository;

import com.qarzbot.entity.Product;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByShop(Shop shop);
    List<Product> findByShopAndNameContainingIgnoreCase(Shop shop, String name);
}
