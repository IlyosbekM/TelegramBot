package com.qarzbot.web;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ShopAdminController {

    private final ShopService shopService;
    private final MembershipService membershipService;
    private final BotConfig botConfig;

    // ── Request bodies ────────────────────────────────────────────────────────
    record CreateShop(String name, String address) {}

    // ── Exception handler ─────────────────────────────────────────────────────
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── POST /api/admin/shops ─────────────────────────────────────────────────
    @PostMapping("/shops")
    public ResponseEntity<?> createShop(@RequestBody CreateShop body) {
        if (body.name() == null || body.name().isBlank()) {
            throw new IllegalArgumentException("Do'kon nomi bo'sh");
        }
        Long adminId = botConfig.getAdminIds() != null && !botConfig.getAdminIds().isEmpty()
                ? botConfig.getAdminIds().get(0)
                : null;
        Shop shop = shopService.create(body.name().trim(), blankToNull(body.address()), null, adminId);
        return ResponseEntity.ok(Map.of("id", shop.getId(), "name", shop.getName()));
    }

    // ── GET /api/admin/shops/{id}/clients ─────────────────────────────────────
    @GetMapping("/shops/{id}/clients")
    public ResponseEntity<?> shopClients(@PathVariable Long id) {
        Shop shop = shopService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Do'kon topilmadi: " + id));
        List<ShopMembership> members = membershipService.acceptedMembers(shop);
        List<Map<String, Object>> result = members.stream()
                .map(m -> Map.<String, Object>of(
                        "telegramId", m.getClient().getTelegramId(),
                        "fullName", m.getClient().getFullName()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
