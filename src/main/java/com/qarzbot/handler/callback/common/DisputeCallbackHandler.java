package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Dispute;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.DisputeRepository;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DisputeService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Callback handler for debt dispute ("e'tiroz") actions.
 * Prefix: {@code etiroz:}
 *
 * Supported data patterns:
 *   etiroz:new:<debtId>    — CLIENT starts a dispute on one of their own debts
 *   etiroz:list            — SELLER lists open disputes for their shop
 *   etiroz:ok:<disputeId>  — SELLER accepts a dispute
 *   etiroz:rej:<disputeId> — SELLER starts the reject-with-reason flow (see DisputeConversationFlow)
 */
@Component
@RequiredArgsConstructor
public class DisputeCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "etiroz:";
    private static final String NEW_CMD = "etiroz:new:";
    private static final String LIST_CMD = "etiroz:list";
    private static final String OK_CMD = "etiroz:ok:";
    private static final String REJ_CMD = "etiroz:rej:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int LIST_CAP = 10;

    private final DisputeService disputeService;
    private final DisputeRepository disputeRepository;
    private final DebtRepository debtRepository;
    private final UserService userService;
    private final AuditService auditService;
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
        Lang lang = Lang.fromStored(user.getLanguage());

        if (data.startsWith(NEW_CMD)) {
            handleNew(user, chatId, data, lang);
        } else if (data.equals(LIST_CMD)) {
            handleList(user, chatId, lang);
        } else if (data.startsWith(OK_CMD)) {
            handleAccept(user, chatId, data);
        } else if (data.startsWith(REJ_CMD)) {
            handleRejectStart(user, chatId, data, lang);
        }
    }

    // ── etiroz:new:<debtId> — CLIENT ─────────────────────────────────────────

    private void handleNew(BotUser user, Long chatId, String data, Lang lang) {
        if (user.getRole() != Role.CLIENT) return;

        Long debtId;
        try {
            debtId = Long.parseLong(data.substring(NEW_CMD.length()));
        } catch (NumberFormatException e) {
            return;
        }

        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        boolean eligible = debtOpt.isPresent()
                && debtOpt.get().getClient().getTelegramId().equals(user.getTelegramId())
                && (debtOpt.get().getStatus() == Debt.DebtStatus.ACTIVE || debtOpt.get().getStatus() == Debt.DebtStatus.OVERDUE)
                && !disputeRepository.existsByDebtAndStatus(debtOpt.get(), Dispute.DisputeStatus.OPEN);
        if (!eligible) {
            messenger.send(chatId, loc.t(user, "etiroz.err_cannot"));
            return;
        }

        userService.setState(user.getTelegramId(), "ETIROZ_REASON:" + debtId);
        messenger.send(chatId, loc.t(user, "etiroz.ask_reason"), KeyboardFactory.cancel(lang));
    }

    // ── etiroz:list — SELLER ─────────────────────────────────────────────────

    private void handleList(BotUser user, Long chatId, Lang lang) {
        if (user.getRole() != Role.SELLER) return;
        Shop shop = user.getShop();
        if (shop == null) {
            messenger.send(chatId, loc.t(user, "etiroz.no_shop"));
            return;
        }

        List<Dispute> disputes = disputeService.openForShop(shop);
        if (disputes.isEmpty()) {
            messenger.send(chatId, loc.t(user, "etiroz.list_empty"));
            return;
        }

        int shown = 0;
        for (Dispute d : disputes) {
            if (shown >= LIST_CAP) break;
            Debt debt = d.getDebt();
            String text = loc.t(user, "etiroz.list_item",
                    d.getClient().getFullName(),
                    debt.getId(),
                    MessageFormatter.money(debt.getRemainingAmount(), shop.getCurrency(), lang),
                    d.getReason(),
                    d.getCreatedAt().format(DATE_FMT));
            messenger.send(chatId, text, KeyboardFactory.acceptReject("etiroz:ok:", "etiroz:rej:", d.getId()));
            shown++;
        }
    }

    // ── etiroz:ok:<disputeId> — SELLER ───────────────────────────────────────

    private void handleAccept(BotUser user, Long chatId, String data) {
        if (user.getRole() != Role.SELLER) return;
        Shop shop = user.getShop();
        if (shop == null) {
            messenger.send(chatId, loc.t(user, "etiroz.no_shop"));
            return;
        }

        Long disputeId;
        try {
            disputeId = Long.parseLong(data.substring(OK_CMD.length()));
        } catch (NumberFormatException e) {
            return;
        }

        Dispute dispute;
        try {
            dispute = disputeService.accept(disputeId, shop);
        } catch (IllegalArgumentException e) {
            messenger.send(chatId, "❌ " + e.getMessage());
            return;
        }

        Debt debt = dispute.getDebt();
        messenger.send(chatId, loc.t(user, "etiroz.accepted_seller", debt.getId()));

        try {
            BotUser client = dispute.getClient();
            Lang clientLang = Lang.fromStored(client.getLanguage());
            messenger.send(client.getTelegramId(),
                    loc.t(clientLang, "etiroz.accepted_client", shop.getName(), debt.getId()));
        } catch (Exception ignored) {}

        try {
            auditService.log(shop, user.getTelegramId(), "DISPUTE_ACCEPT", debt.getId(),
                    "E'tiroz #" + dispute.getId() + " qabul qilindi");
        } catch (Exception ignored) {}
    }

    // ── etiroz:rej:<disputeId> — SELLER ──────────────────────────────────────

    private void handleRejectStart(BotUser user, Long chatId, String data, Lang lang) {
        if (user.getRole() != Role.SELLER) return;
        Shop shop = user.getShop();
        if (shop == null) {
            messenger.send(chatId, loc.t(user, "etiroz.no_shop"));
            return;
        }

        Long disputeId;
        try {
            disputeId = Long.parseLong(data.substring(REJ_CMD.length()));
        } catch (NumberFormatException e) {
            return;
        }

        Optional<Dispute> disputeOpt = disputeRepository.findByIdFetched(disputeId);
        boolean valid = disputeOpt.isPresent()
                && disputeOpt.get().getStatus() == Dispute.DisputeStatus.OPEN
                && disputeOpt.get().getDebt().getShop().getId().equals(shop.getId());
        if (!valid) {
            messenger.send(chatId, loc.t(user, "etiroz.err_cannot"));
            return;
        }

        userService.setState(user.getTelegramId(), "ETIROZ_REJ_REASON:" + disputeId);
        messenger.send(chatId, loc.t(user, "etiroz.ask_reject_reason"), KeyboardFactory.cancel(lang));
    }
}
