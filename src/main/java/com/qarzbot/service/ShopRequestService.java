package com.qarzbot.service;

import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.repository.ShopRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopRequestService {

    private final ShopRequestRepository shopRequestRepository;
    private final ShopService shopService;
    private final UserService userService;

    @Transactional
    public ShopRequest create(Long requesterTelegramId, String name, String address) {
        ShopRequest req = ShopRequest.builder()
                .requesterTelegramId(requesterTelegramId)
                .name(name)
                .address(address)
                .build();
        return shopRequestRepository.save(req);
    }

    @Transactional
    public Shop approve(Long requestId) {
        ShopRequest req = shopRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        if (req.getStatus() != ShopRequest.Status.PENDING) {
            throw new IllegalArgumentException("So'rov allaqachon ko'rib chiqilgan");
        }
        Shop shop = shopService.create(req.getName(), req.getAddress(), null, req.getRequesterTelegramId());
        userService.assignSeller(req.getRequesterTelegramId(), shop);
        req.setStatus(ShopRequest.Status.APPROVED);
        req.setDecidedAt(LocalDateTime.now());
        shopRequestRepository.save(req);
        return shop;
    }

    @Transactional
    public ShopRequest reject(Long requestId, String reason) {
        ShopRequest req = shopRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        if (req.getStatus() != ShopRequest.Status.PENDING) {
            throw new IllegalArgumentException("So'rov allaqachon ko'rib chiqilgan");
        }
        req.setStatus(ShopRequest.Status.REJECTED);
        req.setRejectReason(reason);
        req.setDecidedAt(LocalDateTime.now());
        return shopRequestRepository.save(req);
    }

    public Optional<ShopRequest> findById(Long id) {
        return shopRequestRepository.findById(id);
    }
}
