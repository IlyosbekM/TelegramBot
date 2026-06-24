package com.qarzbot.web;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Shop;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;
    private final ShopService shopService;
    private final BotMessenger messenger;

    // ── Request bodies ────────────────────────────────────────────────────────
    record AssignShop(Long shopId) {}

    // ── Exception handler ─────────────────────────────────────────────────────
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── POST /api/admin/users/{id}/make-seller ────────────────────────────────
    @PostMapping("/users/{id}/make-seller")
    public ResponseEntity<?> makeSeller(@PathVariable Long id, @RequestBody AssignShop body) {
        if (body.shopId() == null) {
            throw new IllegalArgumentException("shopId bo'sh bo'lmasin");
        }
        Shop shop = shopService.findById(body.shopId())
                .orElseThrow(() -> new IllegalArgumentException("Do'kon topilmadi: " + body.shopId()));
        BotUser user = userService.assignSeller(id, shop);
        try {
            messenger.send(
                    user.getTelegramId(),
                    "🎉 Siz \"" + shop.getName() + "\" do'koniga sotuvchi etib tayinlandingiz. /start bosing."
            );
        } catch (Exception ex) {
            log.warn("Foydalanuvchiga Telegram xabari yuborilmadi (make-seller): {}", ex.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/users/{id}/demote ────────────────────────────────────
    @PostMapping("/users/{id}/demote")
    public ResponseEntity<?> demote(@PathVariable Long id) {
        BotUser user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi: " + id));
        userService.demoteToClient(id);
        try {
            messenger.send(
                    user.getTelegramId(),
                    "Sizning rolingiz klientga o'zgartirildi. /start bosing."
            );
        } catch (Exception ex) {
            log.warn("Foydalanuvchiga Telegram xabari yuborilmadi (demote): {}", ex.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
