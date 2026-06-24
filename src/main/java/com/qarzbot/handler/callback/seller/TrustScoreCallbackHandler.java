package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.TrustScoreService;
import com.qarzbot.service.UserService;
import com.qarzbot.service.dto.TrustScore;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handles callback data starting with "trust:".
 *
 * Format: trust:<clientTelegramId>
 *
 * Renders a Markdown trust-score card for the given client in the seller's shop.
 * Only usable by SELLER role users who have a shop assigned.
 */
@Component
@RequiredArgsConstructor
public class TrustScoreCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "trust:";

    private final TrustScoreService trustScoreService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();

        Shop shop = user.getShop();
        if (shop == null) {
            messenger.send(chatId, loc.t(user, "trust.no_shop"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        // Parse client telegram id
        Long clientId;
        try {
            clientId = Long.parseLong(data.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            messenger.send(chatId, loc.t(user, "trust.invalid_id"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        BotUser client = userService.findById(clientId).orElse(null);
        if (client == null) {
            messenger.send(chatId, loc.t(user, "trust.client_not_found"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        TrustScore ts = trustScoreService.forClientInShop(client, shop);
        Lang lang = Lang.fromStored(user.getLanguage());

        String ratingLabel = loc.t(lang, ts.ratingKey());
        String scoreBar = buildScoreBar(ts.score());
        String limit = MessageFormatter.money(ts.recommendedLimit(), shop.getCurrency(), lang);

        String card = loc.t(lang, "trust.card",
                client.getFullName(),
                ts.score(),
                scoreBar,
                ratingLabel,
                ts.totalDebts(),
                ts.paidDebts(),
                ts.overdueDebts(),
                ts.onTimeRatePct(),
                limit
        );

        messenger.sendMarkdown(chatId, card);
        messenger.answerCallback(callbackQueryId, "");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Builds a simple visual progress bar: ████░░░░░░ (10 chars). */
    private static String buildScoreBar(int score) {
        int filled = Math.round(score / 10f);
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }
}
