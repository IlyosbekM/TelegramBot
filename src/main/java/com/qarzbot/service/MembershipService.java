package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.repository.ShopMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final ShopMembershipRepository membershipRepository;

    @Transactional
    public ShopMembership request(BotUser client, Shop shop) {
        Optional<ShopMembership> existing = membershipRepository.findByClientAndShop(client, shop);
        if (existing.isPresent()) {
            ShopMembership m = existing.get();
            switch (m.getStatus()) {
                case ACCEPTED:
                    throw new IllegalStateException("Siz allaqachon bu do'konga bog'langansiz");
                case PENDING:
                    throw new IllegalStateException("So'rov allaqachon yuborilgan, sotuvchi javobini kuting");
                case REJECTED:
                    m.setStatus(ShopMembership.MembershipStatus.PENDING);
                    m.setDecidedAt(null);
                    return membershipRepository.save(m);
                default:
                    break;
            }
        }
        ShopMembership membership = ShopMembership.builder()
                .client(client)
                .shop(shop)
                .build();
        return membershipRepository.save(membership);
    }

    @Transactional
    public ShopMembership accept(Long id) {
        ShopMembership membership = membershipRepository.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        membership.setStatus(ShopMembership.MembershipStatus.ACCEPTED);
        membership.setDecidedAt(LocalDateTime.now());
        return membershipRepository.save(membership);
    }

    @Transactional
    public ShopMembership reject(Long id) {
        ShopMembership membership = membershipRepository.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        membership.setStatus(ShopMembership.MembershipStatus.REJECTED);
        membership.setDecidedAt(LocalDateTime.now());
        return membershipRepository.save(membership);
    }

    public List<ShopMembership> acceptedMembers(Shop shop) {
        return membershipRepository.findByShopAndStatusFetched(shop, ShopMembership.MembershipStatus.ACCEPTED);
    }

    public List<ShopMembership> pendingRequests(Shop shop) {
        return membershipRepository.findByShopAndStatusFetched(shop, ShopMembership.MembershipStatus.PENDING);
    }

    public List<ShopMembership> acceptedShopsOfClient(BotUser client) {
        return membershipRepository.findByClientAndStatusFetched(client, ShopMembership.MembershipStatus.ACCEPTED);
    }

    public boolean isAcceptedMember(BotUser client, Shop shop) {
        return membershipRepository.existsByClientAndShopAndStatus(client, shop, ShopMembership.MembershipStatus.ACCEPTED);
    }

    public Optional<ShopMembership> findByIdFetched(Long id) {
        return membershipRepository.findByIdFetched(id);
    }
}
