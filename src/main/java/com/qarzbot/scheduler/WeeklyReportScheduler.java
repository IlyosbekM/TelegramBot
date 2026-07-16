package com.qarzbot.scheduler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.ExcelExportService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Har haftaning dushanba kuni ertalab soat 08:00 da har bir do'kon sotuvchilariga
 * o'sha do'kon bo'yicha to'liq Excel hisobotni avtomatik yuboradi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final ShopService shopService;
    private final UserService userService;
    private final DebtService debtService;
    private final ExcelExportService excelExportService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyReports() {
        List<Shop> shops = shopService.findAll();
        log.info("Haftalik Excel hisobot: {} ta do'kon tekshirilmoqda", shops.size());

        int shopsSent = 0;
        int sellersSent = 0;

        for (Shop shop : shops) {
            try {
                List<Debt> debts = debtService.findByShop(shop);
                if (debts.isEmpty()) {
                    continue;
                }

                List<BotUser> sellers = userService.findSellersByShop(shop);
                boolean anySent = false;

                for (BotUser seller : sellers) {
                    try {
                        Lang lang = Lang.fromStored(seller.getLanguage());
                        byte[] bytes = excelExportService.shopWorkbook(shop, lang);
                        String filename = "haftalik_" + sanitize(shop.getName()) + "_" + LocalDate.now() + ".xlsx";
                        String caption = loc.t(lang, "excel.weekly_caption", shop.getName());
                        messenger.sendDocument(seller.getTelegramId(), bytes, filename, caption);
                        sellersSent++;
                        anySent = true;
                    } catch (Exception e) {
                        log.warn("Haftalik hisobot yuborishda xato (shop={}, seller={}): {}",
                                shop.getName(), seller.getTelegramId(), e.getMessage());
                    }
                }

                if (anySent) {
                    shopsSent++;
                }
            } catch (Exception e) {
                log.warn("Haftalik hisobot yaratishda xato (shop={}): {}", shop.getName(), e.getMessage());
            }
        }

        log.info("Haftalik Excel hisobot yakunlandi: {} ta do'kon, {} ta sotuvchiga yuborildi", shopsSent, sellersSent);
    }

    private String sanitize(String input) {
        if (input == null) {
            return "shop";
        }
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
