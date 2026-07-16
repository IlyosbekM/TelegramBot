package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Dispute;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DisputeService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

/**
 * Conversation flow for the debt dispute ("e'tiroz") free-text steps.
 * State prefix: {@code ETIROZ_}
 *
 * States:
 *   ETIROZ_REASON:<debtId>        — CLIENT is typing the dispute reason
 *   ETIROZ_REJ_REASON:<disputeId> — SELLER is typing the rejection reason
 */
@Component
@RequiredArgsConstructor
public class DisputeConversationFlow implements ConversationFlow {

    private final DisputeService disputeService;
    private final UserService userService;
    private final AuditService auditService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("ETIROZ_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Long chatId = msg.getChatId();

        // ── ETIROZ_REASON:<debtId> — client types the dispute reason ───────────
        if (state.startsWith("ETIROZ_REASON:")) {
            handleReason(user, chatId, state, text);
            return;
        }

        // ── ETIROZ_REJ_REASON:<disputeId> — seller types the rejection reason ──
        if (state.startsWith("ETIROZ_REJ_REASON:")) {
            handleRejectReason(user, chatId, state, text);
            return;
        }

        // Holat ETIROZ_ bilan boshlanadi, lekin hech bir bosqichga mos kelmadi —
        // qotib qolmaslik uchun state'ni tozalab, asosiy menyuga qaytaramiz.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
    }

    // ── ETIROZ_REASON:<debtId> ────────────────────────────────────────────────

    private void handleReason(BotUser user, Long chatId, String state, String text) {
        Long debtId;
        try {
            debtId = Long.parseLong(state.substring("ETIROZ_REASON:".length()));
        } catch (NumberFormatException e) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
            return;
        }

        Dispute dispute;
        try {
            dispute = disputeService.open(debtId, user.getTelegramId(), text);
        } catch (IllegalArgumentException e) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, "❌ " + e.getMessage());
            return;
        }

        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "etiroz.created"));

        // Do'kon sotuvchilariga har birining o'z tilida, accept/reject tugmalari bilan xabar.
        Shop shop = dispute.getDebt().getShop();
        List<BotUser> sellers = userService.findSellersByShop(shop);
        for (BotUser seller : sellers) {
            try {
                Lang sellerLang = Lang.fromStored(seller.getLanguage());
                String sellerText = loc.t(sellerLang, "etiroz.notify_seller",
                        user.getFullName(), dispute.getDebt().getId(), dispute.getReason());
                messenger.send(seller.getTelegramId(), sellerText,
                        KeyboardFactory.acceptReject("etiroz:ok:", "etiroz:rej:", dispute.getId()));
            } catch (Exception ignored) {}
        }
    }

    // ── ETIROZ_REJ_REASON:<disputeId> ─────────────────────────────────────────

    private void handleRejectReason(BotUser user, Long chatId, String state, String text) {
        Long disputeId;
        try {
            disputeId = Long.parseLong(state.substring("ETIROZ_REJ_REASON:".length()));
        } catch (NumberFormatException e) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
            return;
        }

        Shop shop = user.getShop();
        if (shop == null) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "etiroz.no_shop"));
            return;
        }

        Dispute dispute;
        try {
            dispute = disputeService.reject(disputeId, shop, text);
        } catch (IllegalArgumentException e) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, "❌ " + e.getMessage());
            return;
        }

        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "etiroz.rejected_seller"));

        try {
            BotUser client = dispute.getClient();
            Lang clientLang = Lang.fromStored(client.getLanguage());
            messenger.send(client.getTelegramId(), loc.t(clientLang, "etiroz.rejected_client",
                    shop.getName(), dispute.getDebt().getId(), dispute.getResolutionNote()));
        } catch (Exception ignored) {}

        try {
            auditService.log(shop, user.getTelegramId(), "DISPUTE_REJECT",
                    dispute.getDebt().getId(), dispute.getResolutionNote());
        } catch (Exception ignored) {}
    }
}
