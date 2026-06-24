package com.qarzbot.web;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.repository.ShopRequestRepository;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.PaymentRequestService;
import com.qarzbot.service.ShopRequestService;
import com.qarzbot.service.ShopService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RequestAdminController {

    private final ShopService shopService;
    private final ShopRequestService shopRequestService;
    private final ShopRequestRepository shopRequestRepository;
    private final MembershipService membershipService;
    private final PaymentRequestService paymentRequestService;
    private final BotMessenger messenger;
    private final BotConfig botConfig;

    // ── Request bodies ────────────────────────────────────────────────────────
    record RejectReason(String reason) {}

    // ── Exception handler ─────────────────────────────────────────────────────
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── GET /api/admin/requests ───────────────────────────────────────────────
    @GetMapping("/requests")
    public ResponseEntity<?> pendingRequests() {
        // 1. Shop requests (PENDING)
        List<Map<String, Object>> shopRequests = new ArrayList<>();
        for (ShopRequest sr : shopRequestRepository.findByStatus(ShopRequest.Status.PENDING)) {
            shopRequests.add(Map.of(
                    "id", sr.getId(),
                    "name", sr.getName(),
                    "address", sr.getAddress() != null ? sr.getAddress() : "",
                    "requesterTelegramId", sr.getRequesterTelegramId()
            ));
        }

        // 2. Membership pending requests (across all shops)
        List<Map<String, Object>> memberships = new ArrayList<>();
        for (Shop shop : shopService.findAll()) {
            for (ShopMembership m : membershipService.pendingRequests(shop)) {
                memberships.add(Map.of(
                        "id", m.getId(),
                        "clientName", m.getClient().getFullName(),
                        "shopName", m.getShop().getName()
                ));
            }
        }

        // 3. Payment requests (PENDING, across all shops)
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Shop shop : shopService.findAll()) {
            for (PaymentRequest r : paymentRequestService.pendingForShop(shop)) {
                payments.add(Map.of(
                        "id", r.getId(),
                        "clientName", r.getDebt().getClient().getFullName(),
                        "shopName", r.getDebt().getShop().getName(),
                        "amount", MessageFormatter.money(r.getAmount()),
                        "debtId", r.getDebt().getId()
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "shopRequests", shopRequests,
                "memberships", memberships,
                "payments", payments
        ));
    }

    // ── POST /api/admin/requests/shop/{id}/approve ────────────────────────────
    @PostMapping("/requests/shop/{id}/approve")
    public ResponseEntity<?> approveShopRequest(@PathVariable Long id) {
        // Capture requester BEFORE approve (approve changes status)
        Long requesterTgId = shopRequestService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi: " + id))
                .getRequesterTelegramId();

        Shop shop = shopRequestService.approve(id);

        try {
            messenger.send(requesterTgId,
                    "🎉 Do'kon ochish so'rovingiz tasdiqlandi! Siz \"" + shop.getName()
                    + "\" do'koniga sotuvchi etib tayinlandingiz. /start bosing.");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/requests/shop/{id}/reject ─────────────────────────────
    @PostMapping("/requests/shop/{id}/reject")
    public ResponseEntity<?> rejectShopRequest(@PathVariable Long id,
                                               @RequestBody(required = false) RejectReason body) {
        Long requesterTgId = shopRequestService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi: " + id))
                .getRequesterTelegramId();

        String reason = body != null ? blankToNull(body.reason()) : null;
        shopRequestService.reject(id, reason);

        try {
            String msg = "❌ Do'kon ochish so'rovingiz rad etildi."
                    + (reason != null ? "\nSabab: " + reason : "");
            messenger.send(requesterTgId, msg);
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/requests/membership/{id}/accept ──────────────────────
    @PostMapping("/requests/membership/{id}/accept")
    public ResponseEntity<?> acceptMembership(@PathVariable Long id) {
        ShopMembership m = membershipService.accept(id);

        try {
            messenger.send(m.getClient().getTelegramId(),
                    "✅ Siz " + m.getShop().getName() + " do'koniga bog'landingiz.");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/requests/membership/{id}/reject ───────────────────────
    @PostMapping("/requests/membership/{id}/reject")
    public ResponseEntity<?> rejectMembership(@PathVariable Long id) {
        ShopMembership m = membershipService.reject(id);

        try {
            messenger.send(m.getClient().getTelegramId(),
                    "❌ " + m.getShop().getName() + " bog'lanish so'rovingiz rad etildi.");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/requests/payment/{id}/confirm ─────────────────────────
    @PostMapping("/requests/payment/{id}/confirm")
    public ResponseEntity<?> confirmPayment(@PathVariable Long id) {
        // Capture debt + amount + client BEFORE confirm
        PaymentRequest req = paymentRequestService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("To'lov so'rovi topilmadi: " + id));
        var debt = req.getDebt();
        var amount = req.getAmount();
        Long clientTgId = debt.getClient().getTelegramId();

        Long sellerTgId = botConfig.getAdminIds() != null && !botConfig.getAdminIds().isEmpty()
                ? botConfig.getAdminIds().get(0)
                : null;
        paymentRequestService.confirm(id, sellerTgId);

        try {
            messenger.send(clientTgId, "✅ " + MessageFormatter.money(amount) + " to'lovingiz tasdiqlandi.");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/requests/payment/{id}/reject ──────────────────────────
    @PostMapping("/requests/payment/{id}/reject")
    public ResponseEntity<?> rejectPayment(@PathVariable Long id) {
        PaymentRequest req = paymentRequestService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("To'lov so'rovi topilmadi: " + id));
        Long clientTgId = req.getDebt().getClient().getTelegramId();

        paymentRequestService.reject(id);

        try {
            messenger.send(clientTgId, "❌ To'lov so'rovingiz rad etildi.");
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
