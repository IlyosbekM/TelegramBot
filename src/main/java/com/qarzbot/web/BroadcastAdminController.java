package com.qarzbot.web;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BroadcastAdminController {

    private final UserService userService;
    private final ShopService shopService;
    private final MembershipService membershipService;
    private final BotMessenger messenger;

    // ── Request body ──────────────────────────────────────────────────────────
    record Broadcast(String target, Long shopId, List<Long> ids, String text) {}

    // ── Exception handler ─────────────────────────────────────────────────────
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── POST /api/admin/broadcast ─────────────────────────────────────────────
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(@RequestBody Broadcast body) {
        if (body.text() == null || body.text().isBlank()) {
            throw new IllegalArgumentException("Matn bo'sh");
        }

        List<BotUser> recipients = new ArrayList<>();

        switch (body.target() == null ? "" : body.target()) {
            case "all" -> {
                userService.findAll().stream()
                        .filter(u -> u.getRole() != Role.ADMIN)
                        .forEach(recipients::add);
            }
            case "shop" -> {
                if (body.shopId() == null) {
                    throw new IllegalArgumentException("shopId majburiy");
                }
                Shop shop = shopService.findById(body.shopId())
                        .orElseThrow(() -> new IllegalArgumentException("Do'kon topilmadi: " + body.shopId()));

                // Deduplicate by telegramId using a LinkedHashMap
                LinkedHashMap<Long, BotUser> deduped = new LinkedHashMap<>();

                // Add clients (accepted members)
                for (ShopMembership m : membershipService.acceptedMembers(shop)) {
                    BotUser client = m.getClient();
                    deduped.put(client.getTelegramId(), client);
                }

                // Add sellers
                for (BotUser seller : userService.findSellersByShop(shop)) {
                    deduped.put(seller.getTelegramId(), seller);
                }

                recipients.addAll(deduped.values());
            }
            case "ids" -> {
                if (body.ids() != null) {
                    for (Long id : body.ids()) {
                        userService.findById(id).ifPresent(recipients::add);
                    }
                }
            }
            default -> throw new IllegalArgumentException("target qiymati: all | shop | ids");
        }

        int sent = messenger.broadcast(recipients, "📢 E'lon:", body.text());
        return ResponseEntity.ok(Map.of("ok", true, "sent", sent));
    }
}
