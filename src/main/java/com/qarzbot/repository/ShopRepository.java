package com.qarzbot.repository;

import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByOwnerTelegramId(Long ownerTelegramId);
    List<Shop> findByNameContainingIgnoreCase(String name);
}
