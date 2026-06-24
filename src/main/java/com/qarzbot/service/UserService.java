package com.qarzbot.service;

import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.repository.BotUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final BotUserRepository userRepository;
    private final BotConfig botConfig;

    public BotUser registerIfAbsent(User tgUser) {
        return userRepository.findById(tgUser.getId()).orElseGet(() -> {
            Role role = isAdmin(tgUser.getId()) ? Role.ADMIN : Role.CLIENT;
            String fullName = (tgUser.getFirstName() != null ? tgUser.getFirstName() : "")
                    + (tgUser.getLastName() != null ? " " + tgUser.getLastName() : "");
            BotUser user = BotUser.builder()
                    .telegramId(tgUser.getId())
                    .fullName(fullName.trim().isEmpty() ? "Foydalanuvchi" : fullName.trim())
                    .username(tgUser.getUserName())
                    .role(role)
                    .language(Lang.fromCode(tgUser.getLanguageCode()).getCode())
                    .build();
            return userRepository.save(user);
        });
    }

    public boolean isAdmin(Long telegramId) {
        return botConfig.getAdminIds() != null && botConfig.getAdminIds().contains(telegramId);
    }

    public Optional<BotUser> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<BotUser> findByPhone(String phone) {
        return userRepository.findByPhoneNumber(phone);
    }

    public BotUser save(BotUser user) {
        return userRepository.save(user);
    }

    public List<BotUser> findSellersByShop(Shop shop) {
        return userRepository.findByShopAndRole(shop, Role.SELLER);
    }

    public void setState(Long telegramId, String state) {
        userRepository.findById(telegramId).ifPresent(u -> {
            u.setState(state);
            userRepository.save(u);
        });
    }

    public void clearState(Long telegramId) {
        setState(telegramId, null);
    }

    public List<BotUser> findAll() {
        return userRepository.findAll();
    }

    @org.springframework.transaction.annotation.Transactional
    public BotUser setLanguage(Long telegramId, String langCode) {
        BotUser u = userRepository.findById(telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));
        u.setLanguage(langCode);
        return userRepository.save(u);
    }

    @org.springframework.transaction.annotation.Transactional
    public BotUser setRemindersEnabled(Long telegramId, boolean enabled) {
        BotUser u = userRepository.findById(telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));
        u.setRemindersEnabled(enabled);
        return userRepository.save(u);
    }

    @org.springframework.transaction.annotation.Transactional
    public BotUser assignSeller(Long userId, Shop shop) {
        BotUser u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));
        u.setRole(Role.SELLER);
        u.setShop(shop);
        return userRepository.save(u);
    }

    @org.springframework.transaction.annotation.Transactional
    public BotUser demoteToClient(Long userId) {
        BotUser u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));
        u.setRole(Role.CLIENT);
        u.setShop(null);
        return userRepository.save(u);
    }
}
