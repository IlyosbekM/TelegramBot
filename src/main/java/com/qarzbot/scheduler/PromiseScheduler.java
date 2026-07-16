package com.qarzbot.scheduler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.PromiseService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * "To'lov va'dasi" (payment promise) feature uchun kunlik scheduler:
 *  - va'da kuni kelganda mijozga eslatma yuboradi;
 *  - va'da kuni o'tib ketgan ochiq va'dalarni KEPT/BROKEN deb baholaydi va
 *    natijasi haqida mijoz + sotuvchilarga xabar beradi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromiseScheduler {

    private final PromiseService promiseService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    /**
     * Har kuni soat 09:15 da — va'da qilingan sana AYNAN bugun bo'lgan ochiq
     * va'dalar bo'yicha mijozlarga eslatma yuboradi.
     */
    @Scheduled(cron = "0 15 9 * * *")
    public void remindDueToday() {
        LocalDate today = LocalDate.now();
        List<PaymentPromise> promises = promiseService.openDueOn(today);
        int sent = 0;

        for (PaymentPromise promise : promises) {
            try {
                Debt debt = promise.getDebt();
                BotUser client = promise.getClient();
                Lang clientLang = Lang.fromStored(client.getLanguage());
                String money = MessageFormatter.money(promise.getAmount(), debt.getShop().getCurrency(), clientLang);
                messenger.send(client.getTelegramId(),
                        loc.t(clientLang, "vada.reminder_today", debt.getShop().getName(), debt.getId(), money));
                sent++;
            } catch (Exception e) {
                log.warn("Va'da eslatmasini yuborishda xato (promiseId={}): {}", promise.getId(), e.getMessage());
            }
        }
        log.info("Bugungi va'da eslatmalari yuborildi: {} ta", sent);
    }

    /**
     * Har kuni soat 09:45 da — muddati o'tgan (promiseDate &lt; bugun) ochiq
     * va'dalarni KEPT/BROKEN deb baholaydi va har ikki tomonga xabar beradi.
     */
    @Scheduled(cron = "0 45 9 * * *")
    public void evaluate() {
        LocalDate today = LocalDate.now();
        List<PaymentPromise> evaluated = promiseService.evaluateOverdue(today);

        int keptCount = 0;
        int brokenCount = 0;

        for (PaymentPromise promise : evaluated) {
            Debt debt = promise.getDebt();
            BotUser client = promise.getClient();

            if (promise.getStatus() == PaymentPromise.PromiseStatus.BROKEN) {
                brokenCount++;
                notifyClientBroken(promise, debt, client);
                notifySellersBroken(promise, debt);
            } else if (promise.getStatus() == PaymentPromise.PromiseStatus.KEPT) {
                keptCount++;
                notifyClientKept(promise, client);
            }
        }

        log.info("Va'dalar baholandi: {} ta bajarildi (KEPT), {} ta buzildi (BROKEN)", keptCount, brokenCount);
    }

    private void notifyClientBroken(PaymentPromise promise, Debt debt, BotUser client) {
        try {
            Lang clientLang = Lang.fromStored(client.getLanguage());
            messenger.send(client.getTelegramId(),
                    loc.t(clientLang, "vada.broken_client", debt.getShop().getName(), debt.getId()));
        } catch (Exception e) {
            log.warn("Mijozga buzilgan va'da xabarini yuborishda xato (promiseId={}): {}", promise.getId(), e.getMessage());
        }
    }

    private void notifySellersBroken(PaymentPromise promise, Debt debt) {
        try {
            List<BotUser> sellers = userService.findSellersByShop(debt.getShop());
            for (BotUser seller : sellers) {
                try {
                    Lang sellerLang = Lang.fromStored(seller.getLanguage());
                    String money = MessageFormatter.money(promise.getAmount(), debt.getShop().getCurrency(), sellerLang);
                    messenger.send(seller.getTelegramId(),
                            loc.t(sellerLang, "vada.broken_seller", promise.getClient().getFullName(), debt.getId(), money));
                } catch (Exception e) {
                    log.warn("Sotuvchiga buzilgan va'da xabarini yuborishda xato (promiseId={}, seller={}): {}",
                            promise.getId(), seller.getTelegramId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Sotuvchilar ro'yxatini olishda xato (promiseId={}): {}", promise.getId(), e.getMessage());
        }
    }

    private void notifyClientKept(PaymentPromise promise, BotUser client) {
        try {
            Lang clientLang = Lang.fromStored(client.getLanguage());
            messenger.send(client.getTelegramId(), loc.t(clientLang, "vada.kept_client"));
        } catch (Exception e) {
            log.warn("Mijozga bajarilgan va'da xabarini yuborishda xato (promiseId={}): {}", promise.getId(), e.getMessage());
        }
    }
}
