package com.qarzbot.scheduler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryScheduler {

    private final ShopService shopService;
    private final DebtService debtService;
    private final UserService userService;
    private final BotMessenger messenger;

    @Scheduled(cron = "0 0 20 * * *")
    public void sendDailySellerSummary() {
        List<Shop> shops = shopService.findAll();
        log.info("Kunlik xulosa yuborilmoqda: {} ta do'kon", shops.size());

        for (Shop shop : shops) {
            List<BotUser> sellers = userService.findSellersByShop(shop);
            if (sellers.isEmpty()) {
                continue;
            }

            List<Debt> active = debtService.findActiveByShop(shop);
            int count = active.size();

            BigDecimal total = debtService.totalActiveDebt(shop);
            if (total == null) {
                total = BigDecimal.ZERO;
            }

            long overdue = active.stream()
                    .filter(d -> d.getDueDate() != null && d.getDueDate().isBefore(LocalDate.now()))
                    .count();

            String message = String.format(
                    "📊 *Kunlik xulosa — %s*\n\n📋 Faol qarzlar: %d ta\n💰 Jami qoldiq: %s\n⚠️ Muddati o'tgan: %d ta",
                    shop.getName(),
                    count,
                    MessageFormatter.money(total),
                    overdue
            );

            for (BotUser seller : sellers) {
                try {
                    messenger.sendMarkdown(seller.getTelegramId(), message);
                } catch (Exception e) {
                    log.warn("Kunlik xulosa yuborishda xato (shop={}, seller={}): {}",
                            shop.getName(), seller.getTelegramId(), e.getMessage());
                }
            }
        }
    }
}
