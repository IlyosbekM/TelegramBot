package com.qarzbot.scheduler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Reminder;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.ReminderRepository;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final DebtService debtService;
    private final ReminderRepository reminderRepository;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    /**
     * Har kuni soat 08:30 da muddati o'tgan ACTIVE qarzlarni OVERDUE holatiga o'tkazadi.
     */
    @Scheduled(cron = "0 30 8 * * *")
    public void markOverdueDebts() {
        int count = debtService.markOverdue(LocalDate.now());
        log.info("Muddati o'tgan deb belgilandi: {} ta qarz", count);
    }

    /**
     * Har kuni soat 09:00 da muddati o'tgan/yaqinlashayotgan qarzlar uchun
     * mijozlarga avtomatik eslatma yuborish.
     * Muddati o'tgan qarzlar uchun do'kon sotuvchilariga ham xabar yuboriladi.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyReminders() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(3);
        List<Debt> debts = debtService.findDueOrOverdue(threshold);
        log.info("Eslatma yuboriladi: {} ta qarz uchun", debts.size());

        for (Debt d : debts) {
            boolean isOverdue = d.getDueDate() != null && d.getDueDate().isBefore(today);

            // Mijozga eslatma (faqat eslatmalar yoqilgan bo'lsa)
            Boolean re = d.getClient().getRemindersEnabled();
            boolean remindClient = (re == null) || re;
            if (remindClient) {
                try {
                    Lang clientLang = Lang.fromStored(d.getClient().getLanguage());
                    String prefix = isOverdue
                            ? loc.t(clientLang, "reminder.overdue")
                            : loc.t(clientLang, "reminder.due_soon");
                    messenger.execute(SendMessage.builder()
                            .chatId(d.getClient().getTelegramId().toString())
                            .text(prefix + "\n\n" + MessageFormatter.formatDebt(d, clientLang))
                            .parseMode("Markdown")
                            .build());
                } catch (Exception e) {
                    log.warn("Mijozga eslatma yuborishda xato (debt={}): {}", d.getId(), e.getMessage());
                }
            }

            // Muddati o'tgan bo'lsa — sotuvchilarga ham xabar
            if (isOverdue) {
                try {
                    List<BotUser> sellers = userService.findSellersByShop(d.getShop());
                    for (BotUser seller : sellers) {
                        try {
                            messenger.execute(SendMessage.builder()
                                    .chatId(seller.getTelegramId().toString())
                                    .text("🔴 Muddati o'tgan qarz:\n"
                                            + "👤 " + d.getClient().getFullName() + "\n"
                                            + "💰 " + MessageFormatter.money(d.getRemainingAmount()) + "\n"
                                            + "Qarz #" + d.getId())
                                    .build());
                        } catch (Exception e) {
                            log.warn("Sotuvchiga eslatma yuborishda xato (debt={}, seller={}): {}",
                                    d.getId(), seller.getTelegramId(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Sotuvchilar ro'yxatini olishda xato (debt={}): {}", d.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Har 5 daqiqada rejalashtirilgan eslatmalarni yuborish.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void sendScheduledReminders() {
        List<Reminder> reminders = reminderRepository.findBySentFalseAndSendAtBefore(LocalDateTime.now());
        for (Reminder r : reminders) {
            try {
                messenger.execute(SendMessage.builder()
                        .chatId(r.getDebt().getClient().getTelegramId().toString())
                        .text(r.getMessage() != null ? r.getMessage()
                                : "⏰ Eslatma: " + MessageFormatter.formatDebt(r.getDebt()))
                        .parseMode("Markdown")
                        .build());
                r.setSent(true);
                r.setSentAt(LocalDateTime.now());
                reminderRepository.save(r);
            } catch (Exception e) {
                log.warn("Eslatma yuborishda xato (id={}): {}", r.getId(), e.getMessage());
            }
        }
    }
}
