package com.qarzbot.repository;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    Optional<BotUser> findByPhoneNumber(String phoneNumber);
    List<BotUser> findByRole(Role role);
    List<BotUser> findByShopAndRole(Shop shop, Role role);
}
