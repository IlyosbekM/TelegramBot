package com.qarzbot.service;

import com.qarzbot.entity.Product;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(Shop shop, String name, BigDecimal price, String unit, Integer stock) {
        Product p = Product.builder()
                .shop(shop)
                .name(name)
                .price(price)
                .unit(unit)
                .stock(stock)
                .build();
        return productRepository.save(p);
    }

    public List<Product> findByShop(Shop shop) {
        return productRepository.findByShop(shop);
    }

    public List<Product> search(Shop shop, String query) {
        return productRepository.findByShopAndNameContainingIgnoreCase(shop, query);
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public Product update(Long id, String name, BigDecimal price, String unit, Integer stock) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mahsulot topilmadi"));
        if (name != null)  p.setName(name);
        if (price != null) p.setPrice(price);
        if (unit != null)  p.setUnit(unit);
        if (stock != null) p.setStock(stock);
        return productRepository.save(p);
    }
}
