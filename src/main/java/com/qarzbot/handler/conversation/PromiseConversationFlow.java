package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.PromiseService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Conversation flow for the "payment promise" (to'lov va'dasi) feature.
 * State prefix: {@code VADA_}
 *
 * States:
 *   VADA_DATE:<debtId> — waiting for the client to enter the promised payment date (dd.MM.yyyy)
 */
@Component
@RequiredArgsConstructor
public class PromiseConversationFlow implements ConversationFlow {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PromiseService promiseService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("VADA_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Long chatId = msg.getChatId();
        Lang lang = Lang.fromStored(user.getLanguage());

        // ── VADA_DATE:<debtId> ───────────────────────────────────────────────
        if (state.startsWith("VADA_DATE:")) {
            String debtIdStr = state.substring("VADA_DATE:".length());
            Long debtId;
            try {
                debtId = Long.parseLong(debtIdStr);
            } catch (NumberFormatException e) {
                // Malformed state — fallback
                userService.clearState(user.getTelegramId());
                messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
                return;
            }

            LocalDate promiseDate;
            try {
                promiseDate = LocalDate.parse(text == null ? "" : text.trim(), DATE_FMT);
            } catch (DateTimeParseException e) {
                messenger.send(chatId, loc.t(user, "vada.err_date"), KeyboardFactory.cancel(lang));
                return;
            }

            try {
                PaymentPromise promise = promiseService.create(debtId, user.getTelegramId(), promiseDate);
                userService.clearState(user.getTelegramId());

                Debt debt = promise.getDebt();
                String money = MessageFormatter.money(promise.getAmount(), debt.getShop().getCurrency(), lang);
                messenger.menu(user, chatId,
                        loc.t(user, "vada.created", promiseDate.format(DATE_FMT), money));

                notifySellers(promise, debt);
            } catch (IllegalArgumentException e) {
                userService.clearState(user.getTelegramId());
                messenger.menu(user, chatId, "❌ " + e.getMessage());
            }
            return;
        }

        // ── FALLBACK: unknown VADA_ state ────────────────────────────────────
        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
    }

    /** Qarz do'konidagi barcha sotuvchilarga, har birining o'z tilida, xabar beradi. */
    private void notifySellers(PaymentPromise promise, Debt debt) {
        List<BotUser> sellers = userService.findSellersByShop(debt.getShop());
        for (BotUser seller : sellers) {
            try {
                Lang sellerLang = Lang.fromStored(seller.getLanguage());
                String money = MessageFormatter.money(promise.getAmount(), debt.getShop().getCurrency(), sellerLang);
                messenger.send(seller.getTelegramId(),
                        loc.t(sellerLang, "vada.notify_seller",
                                promise.getClient().getFullName(),
                                debt.getId(),
                                money,
                                promise.getPromiseDate().format(DATE_FMT)));
            } catch (Exception ignored) {
                // har bir yuborish mustaqil — bittasi qulasa ham qolganlariga ta'sir qilmasin
            }
        }
    }
}
