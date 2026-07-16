package com.qarzbot.handler.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.AuditLog;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.PaymentRequestService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SellerViewService {

    private final BotMessenger messenger;
    private final DebtService debtService;
    private final DebtRepository debtRepository;
    private final MembershipService membershipService;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final UserService userService;
    private final PaymentRequestService paymentRequestService;

    // "➕ Qarz qo'shish" — bog'langan a'zolar ro'yxatidan mijoz tanlash
    public void showMembersForDebt(Message message, Shop shop) {
        List<ShopMembership> members = membershipService.acceptedMembers(shop);
        if (members.isEmpty()) {
            messenger.reply(message, "Hozircha bog'langan mijoz yo'q. Mijozlar do'koningizni topib bog'lanishi kerak.");
            return;
        }
        messenger.replyMarkdown(message, "*👥 Qarz qo'shish uchun mijozni tanlang:*");
        for (ShopMembership m : members) {
            BotUser client = m.getClient();
            String phone = client.getPhoneNumber() != null ? client.getPhoneNumber() : "—";
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text("👤 " + client.getFullName() + " (" + phone + ")")
                    .replyMarkup(KeyboardFactory.singleInline("➕ Qarz", "adddebt:" + client.getTelegramId()))
                    .build());
        }
    }

    public void showActiveDebts(Message message, Shop shop) {
        List<Debt> debts = debtService.findActiveByShop(shop);
        if (debts.isEmpty()) {
            messenger.reply(message, "Faol qarz yo'q.");
            return;
        }
        messenger.replyMarkdown(message, MessageFormatter.formatDebtList(debts));
        int shown = 0;
        for (Debt d : debts) {
            if (shown >= 15) break;
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(MessageFormatter.formatDebt(d))
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.debtActions(d.getId()))
                    .build());
            shown++;
        }
    }

    // "👥 Mijozlar" — faqat bog'langan a'zolar asosida
    public void showClients(Message message, Shop shop) {
        List<ShopMembership> members = membershipService.acceptedMembers(shop);
        if (members.isEmpty()) {
            messenger.reply(message, "Hozircha bog'langan mijoz yo'q.");
            return;
        }
        messenger.replyMarkdown(message, "*👥 Mijozlar:*\n\nJami bog'langan: " + members.size());
        for (ShopMembership m : members) {
            BotUser client = m.getClient();
            String phone = client.getPhoneNumber() != null ? client.getPhoneNumber() : "—";
            BigDecimal balance = debtService.findActiveByClient(client).stream()
                    .filter(d -> d.getShop().getId().equals(shop.getId()))
                    .map(Debt::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String line = "👤 " + client.getFullName() + " (" + phone + ") — " + MessageFormatter.money(balance);
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(line)
                    .replyMarkup(KeyboardFactory.clientActions(client.getTelegramId()))
                    .build());
        }
    }

    public void showReport(Message message, Shop shop) {
        int membersCount = membershipService.acceptedMembers(shop).size();
        List<Debt> activeDebts = debtService.findActiveByShop(shop);
        int activeCount = activeDebts.size();
        BigDecimal totalRemaining = debtService.totalActiveDebt(shop);

        LocalDate today = LocalDate.now();
        long overdueCount = activeDebts.stream()
                .filter(d -> d.getDueDate() != null && d.getDueDate().isBefore(today))
                .count();

        // Client bo'yicha qoldiqni guruhlash, kamayish tartibida TOP-3
        Map<String, BigDecimal> clientTotals = new LinkedHashMap<>();
        for (Debt d : activeDebts) {
            String name = d.getClient().getFullName();
            clientTotals.merge(name, d.getRemainingAmount(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> top3 = clientTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("*📊 ").append(shop.getName()).append(" — hisobot*\n\n");
        sb.append("👥 Bog'langan mijozlar: ").append(membersCount).append("\n");
        sb.append("📋 Faol qarzlar: ").append(activeCount).append("\n");
        sb.append("💰 Jami qoldiq: ").append(MessageFormatter.money(totalRemaining)).append("\n");
        sb.append("⚠️ Muddati o'tgan: ").append(overdueCount).append(" ta\n\n");
        sb.append("*🔝 Eng katta qarzdorlar:*\n");

        if (top3.isEmpty()) {
            sb.append("—");
        } else {
            for (int i = 0; i < top3.size(); i++) {
                Map.Entry<String, BigDecimal> entry = top3.get(i);
                sb.append(i + 1).append(". ").append(entry.getKey())
                  .append(" — ").append(MessageFormatter.money(entry.getValue())).append("\n");
            }
        }

        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.singleInline("📥 Excel yuklab olish", "xls:debts"))
                .build());
    }

    // "📥 So'rovlar" — pending membership va payment request so'rovlari
    public void showRequests(Message message, Shop shop) {
        List<ShopMembership> pendingMemberships = membershipService.pendingRequests(shop);
        List<PaymentRequest> pendingPayments = paymentRequestService.pendingForShop(shop);

        if (pendingMemberships.isEmpty() && pendingPayments.isEmpty()) {
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text("Yangi so'rov yo'q.\n\nQo'shimcha bo'limlar:")
                    .replyMarkup(KeyboardFactory.sellerRequestExtras())
                    .build());
            return;
        }

        for (ShopMembership m : pendingMemberships) {
            BotUser client = m.getClient();
            String phone = client.getPhoneNumber() != null ? client.getPhoneNumber() : "—";
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text("🔔 Bog'lanish: " + client.getFullName() + " (" + phone + ")")
                    .replyMarkup(KeyboardFactory.acceptReject("mbacc:", "mbrej:", m.getId()))
                    .build());
        }

        for (PaymentRequest r : pendingPayments) {
            Debt debt = r.getDebt();
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text("💵 To'lov: " + debt.getClient().getFullName()
                            + ", Qarz #" + debt.getId()
                            + ", So'ralgan: " + MessageFormatter.money(r.getAmount()))
                    .replyMarkup(KeyboardFactory.acceptReject("payok:", "payno:", r.getId()))
                    .build());
        }

        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text("Qo'shimcha bo'limlar:")
                .replyMarkup(KeyboardFactory.sellerRequestExtras())
                .build());
    }

    // "🧾 Tarix" — audit log
    public void showAudit(Message message, Shop shop) {
        List<AuditLog> logs = auditService.recent(shop, 20);
        if (logs.isEmpty()) {
            messenger.reply(message, "Tarix bo'sh.");
            return;
        }
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        StringBuilder sb = new StringBuilder("*🧾 Audit tarixi:*\n\n");
        for (AuditLog log : logs) {
            sb.append(log.getCreatedAt().format(df))
              .append(" — ").append(log.getAction());
            if (log.getDetails() != null && !log.getDetails().isBlank()) {
                sb.append(" ").append(log.getDetails());
            }
            sb.append("\n");
        }
        messenger.replyMarkdown(message, sb.toString());
    }

    public void sendReminders(Message message, Shop shop) {
        List<Debt> debts = debtService.findActiveByShop(shop);
        int sent = 0;
        for (Debt d : debts) {
            try {
                messenger.execute(SendMessage.builder()
                        .chatId(d.getClient().getTelegramId().toString())
                        .text("⏰ *Eslatma:* Sizda " + shop.getName() + " do'konida "
                                + MessageFormatter.money(d.getRemainingAmount()) + " qarz mavjud.")
                        .parseMode("Markdown")
                        .build());
                sent++;
            } catch (Exception ignored) {}
        }
        messenger.reply(message, "✅ " + sent + " ta mijozga eslatma yuborildi.");
    }

    public void showStatistics(Message message, Shop shop) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDate today = LocalDate.now();

        long newCount = debtRepository.countNewDebtsSince(shop, startOfMonth);
        BigDecimal newSum = debtRepository.sumNewDebtsSince(shop, startOfMonth);
        BigDecimal collected = paymentRepository.sumCollectedSince(shop, startOfMonth);
        long collectedCount = paymentRepository.countCollectedSince(shop, startOfMonth);
        long activeCount = debtRepository.countActiveByShop(shop);
        BigDecimal outstanding = debtRepository.totalActiveDebtByShop(shop);
        long overdueCount = debtRepository.countOverdueByShop(shop, today);
        BigDecimal overdueSum = debtRepository.sumOverdueByShop(shop, today);

        // Top-5 debtors by remaining amount
        List<Debt> activeDebts = debtService.findActiveByShop(shop);
        Map<String, BigDecimal> clientTotals = new LinkedHashMap<>();
        for (Debt d : activeDebts) {
            String name = d.getClient().getFullName();
            clientTotals.merge(name, d.getRemainingAmount(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> top5 = clientTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *Statistika — ").append(shop.getName()).append("*\n");
        sb.append("🗓 Shu oy (oy boshidan):\n\n");
        sb.append("🆕 Yangi qarzlar: ").append(newCount).append(" ta — ").append(MessageFormatter.money(newSum)).append("\n");
        sb.append("💸 Yig'ilgan to'lovlar: ").append(collectedCount).append(" ta — ").append(MessageFormatter.money(collected)).append("\n\n");
        sb.append("📊 *Umumiy holat:*\n");
        sb.append("📋 Faol qarzlar: ").append(activeCount).append(" ta\n");
        sb.append("🔴 Qoldiq (jami): ").append(MessageFormatter.money(outstanding)).append("\n");
        sb.append("⚠️ Muddati o'tgan: ").append(overdueCount).append(" ta — ").append(MessageFormatter.money(overdueSum)).append("\n\n");
        sb.append("🏆 *Eng katta qarzdorlar:*\n");

        if (top5.isEmpty()) {
            sb.append("—");
        } else {
            for (int i = 0; i < top5.size(); i++) {
                Map.Entry<String, BigDecimal> entry = top5.get(i);
                sb.append(i + 1).append(". ").append(entry.getKey())
                  .append(" — ").append(MessageFormatter.money(entry.getValue())).append("\n");
            }
        }

        messenger.replyMarkdown(message, sb.toString());
    }

    public void showStatement(Long chatId, BotUser seller, Long clientTelegramId) {
        Shop shop = seller.getShop();
        Optional<BotUser> clientOpt = userService.findById(clientTelegramId);
        if (clientOpt.isEmpty() || shop == null) {
            messenger.send(chatId, "❌ Mijoz topilmadi.");
            return;
        }
        BotUser client = clientOpt.get();
        List<Debt> allDebts = debtService.findAllByClient(client).stream()
                .filter(d -> d.getShop().getId().equals(shop.getId()))
                .toList();
        if (allDebts.isEmpty()) {
            messenger.send(chatId, "Bu mijozda qarz yo'q.");
            return;
        }

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("*📊 ").append(client.getFullName()).append(" — to'liq hisobot*\n");
        sb.append("🏪 ").append(shop.getName()).append("\n\n");

        BigDecimal sumTotal = BigDecimal.ZERO;
        BigDecimal sumPaid = BigDecimal.ZERO;
        BigDecimal sumRemaining = BigDecimal.ZERO;

        for (Debt d : allDebts) {
            String statusEmoji = switch (d.getStatus()) {
                case ACTIVE -> "🔴";
                case PAID -> "✅";
                case OVERDUE -> "⚠️";
                case CANCELLED -> "❌";
            };
            sb.append("#").append(d.getId()).append(" — ").append(statusEmoji).append(" ").append(d.getStatus().name()).append("\n");
            sb.append("  Umumiy: ").append(MessageFormatter.money(d.getTotalAmount()))
              .append(" | To'langan: ").append(MessageFormatter.money(d.getPaidAmount()))
              .append(" | Qoldiq: ").append(MessageFormatter.money(d.getRemainingAmount())).append("\n");

            List<Payment> payments = paymentRepository.findByDebt(d);
            if (!payments.isEmpty()) {
                sb.append("  To'lovlar:\n");
                for (Payment p : payments) {
                    sb.append("    • ").append(p.getPaidAt().format(df))
                      .append(" — ").append(MessageFormatter.money(p.getAmount())).append("\n");
                }
            }
            sb.append("\n");

            sumTotal = sumTotal.add(d.getTotalAmount());
            sumPaid = sumPaid.add(d.getPaidAmount());
            sumRemaining = sumRemaining.add(d.getRemainingAmount());
        }

        sb.append("━━━━━━━━━━━\n");
        sb.append("*Jami umumiy:* ").append(MessageFormatter.money(sumTotal)).append("\n");
        sb.append("*Jami to'langan:* ").append(MessageFormatter.money(sumPaid)).append("\n");
        sb.append("*Jami qoldiq:* ").append(MessageFormatter.money(sumRemaining));

        messenger.sendMarkdown(chatId, sb.toString());
    }

    public void showClientDebts(Long chatId, BotUser seller, Long clientId) {
        Shop shop = seller.getShop();
        if (shop == null) {
            messenger.send(chatId, "❌ Do'kon topilmadi.");
            return;
        }
        Optional<BotUser> clientOpt = userService.findById(clientId);
        if (clientOpt.isEmpty()) {
            messenger.send(chatId, "❌ Mijoz topilmadi.");
            return;
        }
        BotUser client = clientOpt.get();
        List<Debt> clientDebts = debtService.findActiveByClient(client).stream()
                .filter(d -> d.getShop().getId().equals(shop.getId()))
                .toList();
        if (clientDebts.isEmpty()) {
            messenger.send(chatId, client.getFullName() + " — faol qarz yo'q.");
            return;
        }
        messenger.sendMarkdown(chatId, "*📋 " + client.getFullName() + " qarzlari:*");
        int shown = 0;
        for (Debt d : clientDebts) {
            if (shown >= 15) break;
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(MessageFormatter.formatDebt(d))
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.debtActions(d.getId()))
                    .build());
            shown++;
        }
    }
}
