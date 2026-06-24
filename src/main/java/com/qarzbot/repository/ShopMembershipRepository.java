package com.qarzbot.repository;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShopMembershipRepository extends JpaRepository<ShopMembership, Long> {

    Optional<ShopMembership> findByClientAndShop(BotUser client, Shop shop);

    boolean existsByClientAndShopAndStatus(BotUser client, Shop shop, ShopMembership.MembershipStatus status);

    @Query("SELECT m FROM ShopMembership m JOIN FETCH m.client JOIN FETCH m.shop " +
           "WHERE m.shop = :shop AND m.status = :status")
    List<ShopMembership> findByShopAndStatusFetched(@Param("shop") Shop shop,
                                                     @Param("status") ShopMembership.MembershipStatus status);

    @Query("SELECT m FROM ShopMembership m JOIN FETCH m.client JOIN FETCH m.shop " +
           "WHERE m.client = :client AND m.status = :status")
    List<ShopMembership> findByClientAndStatusFetched(@Param("client") BotUser client,
                                                       @Param("status") ShopMembership.MembershipStatus status);

    @Query("SELECT m FROM ShopMembership m JOIN FETCH m.client JOIN FETCH m.shop WHERE m.id = :id")
    Optional<ShopMembership> findByIdFetched(@Param("id") Long id);
}
