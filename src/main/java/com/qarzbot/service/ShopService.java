package com.qarzbot.service;

import com.qarzbot.entity.Shop;
import com.qarzbot.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;

    public Shop create(String name, String address, String phone, Long ownerTelegramId) {
        Shop shop = Shop.builder()
                .name(name)
                .address(address)
                .phoneNumber(phone)
                .ownerTelegramId(ownerTelegramId)
                .build();
        if (shop.getCurrency() == null) {
            shop.setCurrency("UZS");
        }
        return shopRepository.save(shop);
    }

    public Optional<Shop> findById(Long id) {
        return shopRepository.findById(id);
    }

    public List<Shop> findAll() {
        return shopRepository.findAll();
    }

    public List<Shop> findByOwner(Long ownerId) {
        return shopRepository.findByOwnerTelegramId(ownerId);
    }

    public List<Shop> searchByNameOrId(String query) {
        List<Shop> result = new java.util.ArrayList<>();
        try {
            Long id = Long.parseLong(query.trim());
            shopRepository.findById(id).ifPresent(result::add);
        } catch (NumberFormatException ignored) {}
        for (Shop s : shopRepository.findByNameContainingIgnoreCase(query.trim())) {
            if (result.stream().noneMatch(r -> r.getId().equals(s.getId()))) result.add(s);
        }
        return result;
    }
}
