package com.qarzbot.web;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DebtAdminController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DebtService debtService;
    private final ShopService shopService;
    private final UserService userService;
    private final MembershipService membershipService;
    private final BotMessenger messenger;
    private final BotConfig botConfig;

    // ── Request bodies ────────────────────────────────────────────────────────
    record CreateDebt(Long shopId, Long clientId, String amount, String description, String dueDate) {}
    record Pay(String amount, String note) {}
    record Increase(String amount, String note) {}
    record EditDebt(String amount, String description, String dueDate) {}

    // ── Exception handler ─────────────────────────────────────────────────────
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── POST /api/admin/debts ─────────────────────────────────────────────────
    @PostMapping("/debts")
    public ResponseEntity<?> createDebt(@RequestBody CreateDebt body) {
        if (body.shopId() == null) throw new IllegalArgumentException("shopId bo'sh bo'lmasin");
        if (body.clientId() == null) throw new IllegalArgumentException("clientId bo'sh bo'lmasin");
        if (body.amount() == null || body.amount().isBlank()) throw new IllegalArgumentException("Summa bo'sh bo'lmasin");

        Shop shop = shopService.findById(body.shopId())
                .orElseThrow(() -> new IllegalArgumentException("Do'kon topilmadi: " + body.shopId()));
        BotUser client = userService.findById(body.clientId())
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi: " + body.clientId()));

        if (!membershipService.isAcceptedMember(client, shop)) {
            throw new IllegalArgumentException("Mijoz do'konga bog'lanmagan");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(body.amount().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Noto'g'ri summa formati: " + body.amount());
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Summa musbat bo'lishi kerak");
        }

        LocalDate dueDate = null;
        if (!isBlank(body.dueDate())) {
            try {
                dueDate = LocalDate.parse(body.dueDate().trim(), DATE_FMT);
            } catch (Exception e) {
                throw new IllegalArgumentException("Noto'g'ri sana formati (dd.MM.yyyy kerak): " + body.dueDate());
            }
        }

        BotUser seller = resolveSellerForShop(shop);
        Debt debt = debtService.createDebt(shop, client, seller, amount, blankToNull(body.description()), dueDate);

        try {
            messenger.sendMarkdown(
                    client.getTelegramId(),
                    "📌 " + shop.getName() + " do'konida sizga yangi qarz qo'shildi:\n\n"
                            + MessageFormatter.formatDebt(debt)
            );
        } catch (Exception ex) {
            log.warn("Mijozga Telegram xabari yuborilmadi (createDebt): {}", ex.getMessage());
        }

        return ResponseEntity.ok(Map.of("ok", true, "id", debt.getId()));
    }

    // ── POST /api/admin/debts/{id}/pay ────────────────────────────────────────
    @PostMapping("/debts/{id}/pay")
    public ResponseEntity<?> pay(@PathVariable Long id, @RequestBody Pay body) {
        if (isBlank(body.amount())) throw new IllegalArgumentException("Summa bo'sh bo'lmasin");

        BigDecimal amount;
        try {
            amount = new BigDecimal(body.amount().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Noto'g'ri summa formati: " + body.amount());
        }

        // Resolve recordedBy: prefer the shop's first seller, fall back to admin
        Debt debtBefore = debtService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi: " + id));
        Long recordedBy = resolveRecordedBy(debtBefore.getShop());

        debtService.addPayment(id, amount, blankToNull(body.note()), recordedBy);

        // Reload after payment to get updated remaining amount
        Debt updated = debtService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi: " + id));

        try {
            messenger.send(
                    updated.getClient().getTelegramId(),
                    "✅ Qarzingiz uchun " + MessageFormatter.money(amount)
                            + " to'lov qabul qilindi. Qoldiq: "
                            + MessageFormatter.money(updated.getRemainingAmount())
            );
        } catch (Exception ex) {
            log.warn("Mijozga Telegram xabari yuborilmadi (pay): {}", ex.getMessage());
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/debts/{id}/increase ──────────────────────────────────
    @PostMapping("/debts/{id}/increase")
    public ResponseEntity<?> increase(@PathVariable Long id, @RequestBody Increase body) {
        if (isBlank(body.amount())) throw new IllegalArgumentException("Summa bo'sh bo'lmasin");

        BigDecimal amount;
        try {
            amount = new BigDecimal(body.amount().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Noto'g'ri summa formati: " + body.amount());
        }

        Debt debtBefore = debtService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi: " + id));
        Long recordedBy = resolveRecordedBy(debtBefore.getShop());

        Debt updated = debtService.increaseDebt(id, amount, blankToNull(body.note()), recordedBy);

        try {
            messenger.send(
                    updated.getClient().getTelegramId(),
                    "📌 Qarzingizga " + MessageFormatter.money(amount)
                            + " qo'shildi. Yangi qoldiq: "
                            + MessageFormatter.money(updated.getRemainingAmount())
            );
        } catch (Exception ex) {
            log.warn("Mijozga Telegram xabari yuborilmadi (increase): {}", ex.getMessage());
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── PUT /api/admin/debts/{id} ─────────────────────────────────────────────
    @PutMapping("/debts/{id}")
    public ResponseEntity<?> editDebt(@PathVariable Long id, @RequestBody EditDebt body) {
        Debt updated = null;

        if (!isBlank(body.amount())) {
            BigDecimal newTotal;
            try {
                newTotal = new BigDecimal(body.amount().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Noto'g'ri summa formati: " + body.amount());
            }
            updated = debtService.updateAmount(id, newTotal);
            // Notify client about updated remaining
            final Debt snap = updated;
            try {
                messenger.send(
                        snap.getClient().getTelegramId(),
                        "✏️ Qarz #" + id + " summasi yangilandi. Yangi qoldiq: "
                                + MessageFormatter.money(snap.getRemainingAmount())
                );
            } catch (Exception ex) {
                log.warn("Mijozga Telegram xabari yuborilmadi (editDebt-amount): {}", ex.getMessage());
            }
        }

        if (body.description() != null) {
            updated = debtService.updateDescription(id, blankToNull(body.description()));
        }

        if (!isBlank(body.dueDate())) {
            LocalDate due;
            try {
                due = LocalDate.parse(body.dueDate().trim(), DATE_FMT);
            } catch (Exception e) {
                throw new IllegalArgumentException("Noto'g'ri sana formati (dd.MM.yyyy kerak): " + body.dueDate());
            }
            updated = debtService.updateDueDate(id, due);
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── DELETE /api/admin/debts/{id} ──────────────────────────────────────────
    @DeleteMapping("/debts/{id}")
    public ResponseEntity<?> deleteDebt(@PathVariable Long id) {
        debtService.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/admin/debts/{id}/remind ────────────────────────────────────
    @PostMapping("/debts/{id}/remind")
    public ResponseEntity<?> remind(@PathVariable Long id) {
        Debt debt = debtService.findByIdFetched(id)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi: " + id));
        try {
            messenger.sendMarkdown(
                    debt.getClient().getTelegramId(),
                    "⏰ *Eslatma:* Sizda *" + debt.getShop().getName() + "* do'konida "
                            + MessageFormatter.money(debt.getRemainingAmount())
                            + " qarz mavjud.\nQarz #" + debt.getId()
            );
        } catch (Exception ex) {
            log.warn("Mijozga Telegram eslatmasi yuborilmadi (remind): {}", ex.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns the first seller of the shop, or throws if none. */
    private BotUser resolveSellerForShop(Shop shop) {
        return userService.findSellersByShop(shop).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Do'konda sotuvchi yo'q"));
    }

    /**
     * Returns the telegramId to use as 'recordedBy': the shop's first seller, or
     * admin[0] as fallback.
     */
    private Long resolveRecordedBy(Shop shop) {
        return userService.findSellersByShop(shop).stream()
                .findFirst()
                .map(BotUser::getTelegramId)
                .orElseGet(() -> {
                    if (botConfig.getAdminIds() != null && !botConfig.getAdminIds().isEmpty()) {
                        return botConfig.getAdminIds().get(0);
                    }
                    throw new IllegalArgumentException("Do'konda sotuvchi yo'q va admin aniqlanmadi");
                });
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
