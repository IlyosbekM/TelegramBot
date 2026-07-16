package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentPromiseRepository;
import com.qarzbot.service.PromiseService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Callback handler for the "payment promise" (to'lov va'dasi) feature.
 * Prefix: {@code vada:}
 *
 * Supported data patterns:
 *   vada:new:<debtId> — CLIENT starts a payment-promise conversation for one of their debts
 *   vada:list         — SELLER views all open promises for their shop
 */
@Component
@RequiredArgsConstructor
public class PromiseCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "vada:";
    private static final String NEW_CMD = "vada:new:";
    private static final String LIST_CMD = "vada:list";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int LIST_CAP = 20;

    private final PromiseService promiseService;
    private final DebtRepository debtRepository;
    private final PaymentPromiseRepository paymentPromiseRepository;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(BotUser user, Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();
        Lang lang = Lang.fromStored(user.getLanguage());

        if (data.startsWith(NEW_CMD)) {
            handleNew(user, chatId, callbackQueryId, data, lang);
        } else if (data.equals(LIST_CMD)) {
            handleList(user, chatId, callbackQueryId, lang);
        } else {
            messenger.answerCallback(callbackQueryId, "");
        }
    }

    // ── vada:new:<debtId> ────────────────────────────────────────────────────

    private void handleNew(BotUser user, Long chatId, String callbackQueryId, String data, Lang lang) {
        if (user.getRole() != Role.CLIENT) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        Long debtId;
        try {
            debtId = Long.parseLong(data.substring(NEW_CMD.length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        if (!canPromise(debtId, user)) {
            messenger.answerCallback(callbackQueryId, "");
            messenger.send(chatId, loc.t(user, "vada.err_cannot"));
            return;
        }

        userService.setState(user.getTelegramId(), "VADA_DATE:" + debtId);
        messenger.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(loc.t(user, "vada.ask_date"))
                .replyMarkup(KeyboardFactory.cancel(lang))
                .build());
        messenger.answerCallback(callbackQueryId, "");
    }

    /** Qarz mavjudmi, shu mijozniki-mi, ACTIVE/OVERDUE holatidami va ochiq va'dasi yo'qmi. */
    private boolean canPromise(Long debtId, BotUser user) {
        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        if (debtOpt.isEmpty()) {
            return false;
        }
        Debt debt = debtOpt.get();
        if (!debt.getClient().getTelegramId().equals(user.getTelegramId())) {
            return false;
        }
        if (debt.getStatus() != Debt.DebtStatus.ACTIVE && debt.getStatus() != Debt.DebtStatus.OVERDUE) {
            return false;
        }
        return !paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN);
    }

    // ── vada:list ────────────────────────────────────────────────────────────

    private void handleList(BotUser user, Long chatId, String callbackQueryId, Lang lang) {
        if (user.getRole() != Role.SELLER || user.getShop() == null) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        List<PaymentPromise> promises = promiseService.openForShop(user.getShop());
        if (promises.isEmpty()) {
            messenger.send(chatId, loc.t(user, "vada.list_empty"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append(loc.t(user, "vada.list_header")).append("\n\n");

        int cap = Math.min(promises.size(), LIST_CAP);
        for (int i = 0; i < cap; i++) {
            PaymentPromise p = promises.get(i);
            Debt debt = p.getDebt();
            String money = MessageFormatter.money(p.getAmount(), debt.getShop().getCurrency(), lang);
            long daysUntil = ChronoUnit.DAYS.between(today, p.getPromiseDate());
            String daysLabel = daysUntil >= 0
                    ? loc.t(user, "vada.days_left", daysUntil)
                    : loc.t(user, "vada.days_past", -daysUntil);

            sb.append(loc.t(user, "vada.list_item",
                            p.getClient().getFullName(),
                            debt.getId(),
                            money,
                            p.getPromiseDate().format(DATE_FMT),
                            daysLabel))
              .append("\n");
        }

        messenger.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(sb.toString())
                .parseMode("Markdown")
                .build());
        messenger.answerCallback(callbackQueryId, "");
    }
}
