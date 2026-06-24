package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.LeaderboardService;
import com.qarzbot.service.LeaderboardService.Entry;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Handles callback data starting with "lead:".
 *
 * Supported actions:
 *   lead:reliable  — top-5 most reliable clients (by trust score)
 *   lead:debtors   — top-5 biggest debtors (by remaining active debt)
 *
 * Only usable by SELLER role users who have a shop assigned.
 */
@Component
@RequiredArgsConstructor
public class LeaderboardCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "lead:";

    private final LeaderboardService leaderboardService;
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
            messenger.send(chatId, loc.t(user, "lead.no_shop"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        Lang lang = Lang.fromStored(user.getLanguage());
        String action = data.substring(PREFIX.length());

        switch (action) {
            case "reliable" -> {
                List<Entry> entries = leaderboardService.mostReliable(shop);
                String text = buildReliableList(entries, shop, lang, loc);
                messenger.sendMarkdown(chatId, text);
            }
            case "debtors" -> {
                List<Entry> entries = leaderboardService.topDebtors(shop);
                String text = buildDebtorsList(entries, shop, lang, loc);
                messenger.sendMarkdown(chatId, text);
            }
            default -> messenger.send(chatId, loc.t(user, "lead.unknown_action"));
        }

        messenger.answerCallback(callbackQueryId, "");
    }

    // ── Rendering helpers ─────────────────────────────────────────────────────

    private static String buildReliableList(List<Entry> entries, Shop shop, Lang lang, Loc loc) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(loc.t(lang, "lead.reliable.title", shop.getName())).append("*\n\n");

        if (entries.isEmpty()) {
            sb.append(loc.t(lang, "lead.empty"));
            return sb.toString();
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String badge = e.badge().isEmpty() ? (i + 1) + "." : e.badge();
            sb.append(badge).append(" *").append(escapeMarkdown(e.name())).append("*")
              .append(" — ").append(e.score()).append(" ball")
              .append(" (").append(loc.t(lang, scoreRatingKey(e.score()))).append(")")
              .append("\n");
        }
        return sb.toString();
    }

    private static String buildDebtorsList(List<Entry> entries, Shop shop, Lang lang, Loc loc) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(loc.t(lang, "lead.debtors.title", shop.getName())).append("*\n\n");

        if (entries.isEmpty()) {
            sb.append(loc.t(lang, "lead.empty"));
            return sb.toString();
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String badge = e.badge().isEmpty() ? (i + 1) + "." : e.badge();
            String amount = MessageFormatter.money(e.value(), shop.getCurrency(), lang);
            sb.append(badge).append(" *").append(escapeMarkdown(e.name())).append("*")
              .append(" — ").append(amount)
              .append("\n");
        }
        return sb.toString();
    }

    /** Returns i18n key for rating band (mirrors TrustScore.ratingKeyForScore). */
    private static String scoreRatingKey(int score) {
        if (score >= 85) return "trust.rating.excellent";
        if (score >= 70) return "trust.rating.good";
        if (score >= 50) return "trust.rating.fair";
        return "trust.rating.poor";
    }

    /** Escapes Markdown special chars in user-provided strings. */
    private static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("*", "\\*").replace("_", "\\_").replace("`", "\\`").replace("[", "\\[");
    }
}
