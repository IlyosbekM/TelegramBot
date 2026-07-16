package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.PaymentUndoService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Callback handler for undoing the latest payment on a debt (seller-only, within 24 hours
 * of recording it). Two-step confirmation UX.
 * Prefix: {@code payundo:}
 * Supported data patterns:
 *   payundo:ask:<debtId>    — show the latest undoable payment and ask for confirmation
 *   payundo:yes:<paymentId> — perform the undo
 *   payundo:no              — cancel (toast only)
 */
@Component
@RequiredArgsConstructor
public class PaymentUndoCallbackHandler implements CallbackHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PaymentUndoService paymentUndoService;
    private final DebtRepository debtRepository;
    private final AuditService auditService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("payundo:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();
        Lang lang = Lang.fromStored(user.getLanguage());

        if (data.startsWith("payundo:ask:")) {
            handleAsk(user, chatId, data, lang);
        } else if (data.startsWith("payundo:yes:")) {
            handleYes(user, chatId, data, lang);
        } else if (data.equals("payundo:no")) {
            messenger.answerCallback(callbackQueryId, loc.t(user, "payundo.cancelled"));
        }
    }

    // ── payundo:ask:<debtId> ─────────────────────────────────────────────────

    private void handleAsk(BotUser user, Long chatId, String data, Lang lang) {
        Long debtId;
        try {
            debtId = Long.parseLong(data.substring("payundo:ask:".length()));
        } catch (NumberFormatException e) {
            return;
        }

        Payment p;
        try {
            p = paymentUndoService.lastUndoable(debtId, user);
        } catch (IllegalArgumentException e) {
            messenger.send(chatId, "❌ " + e.getMessage());
            return;
        }

        // lastUndoable() fetches the debt via debtRepository.findByIdFetched, so client + shop
        // are already eagerly join-fetched here — safe to read their non-id fields directly.
        Debt debt = p.getDebt();
        String currency = debt.getShop().getCurrency();

        String text = loc.t(user, "payundo.confirm",
                MessageFormatter.money(p.getAmount(), currency, lang),
                p.getPaidAt().format(DATETIME_FMT),
                debt.getClient().getFullName(),
                debt.getId());

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        new InlineKeyboardRow(InlineKeyboardButton.builder()
                                .text(loc.t(user, "payundo.btn_yes"))
                                .callbackData("payundo:yes:" + p.getId())
                                .build()),
                        new InlineKeyboardRow(InlineKeyboardButton.builder()
                                .text(loc.t(user, "payundo.btn_no"))
                                .callbackData("payundo:no")
                                .build())
                ))
                .build();

        messenger.send(chatId, text, markup);
    }

    // ── payundo:yes:<paymentId> ──────────────────────────────────────────────

    private void handleYes(BotUser user, Long chatId, String data, Lang lang) {
        Long paymentId;
        try {
            paymentId = Long.parseLong(data.substring("payundo:yes:".length()));
        } catch (NumberFormatException e) {
            return;
        }

        Payment p;
        try {
            p = paymentUndoService.undo(paymentId, user);
        } catch (IllegalArgumentException e) {
            messenger.send(chatId, "❌ " + e.getMessage());
            return;
        }

        // IMPORTANT: after undo() returns, p.getDebt() is only guaranteed to have its own plain
        // columns populated — its shop/client LAZY associations may be uninitialized proxies tied
        // to the (by now closed) service-layer transaction. Only .getId() is safe to call on them.
        // Everything else (shop name/currency, client name/telegram id/language) is re-read via a
        // fresh, fully join-fetched query so no lazy access happens on a detached row.
        BigDecimal amount = p.getAmount();
        Long debtId = p.getDebt().getId();

        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        if (debtOpt.isEmpty()) {
            messenger.send(chatId, "❌ " + loc.t(user, "err.debt_not_found_plain"));
            return;
        }
        Debt debt = debtOpt.get();
        String currency = debt.getShop().getCurrency();
        String newRemaining = MessageFormatter.money(debt.getRemainingAmount(), currency, lang);

        messenger.send(chatId, loc.t(user, "payundo.done",
                MessageFormatter.money(amount, currency, lang), debtId, newRemaining));

        try {
            BotUser client = debt.getClient();
            Lang clientLang = Lang.fromStored(client.getLanguage());
            String clientRemaining = MessageFormatter.money(debt.getRemainingAmount(), currency, clientLang);
            messenger.send(client.getTelegramId(), loc.t(clientLang, "payundo.client_notice",
                    debt.getShop().getName(),
                    MessageFormatter.money(amount, currency, clientLang),
                    debtId,
                    clientRemaining));
        } catch (Exception ignored) {
        }

        try {
            auditService.log(debt.getShop(), user.getTelegramId(), "PAYMENT_UNDO", debtId,
                    MessageFormatter.money(amount, currency, lang));
        } catch (Exception ignored) {
        }
    }
}
