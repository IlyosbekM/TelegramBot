package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.DebtSearchService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

/**
 * Conversation flow for the advanced debt name-search feature.
 * Handles all states prefixed with {@code FILTER_}.
 *
 * States:
 * <ul>
 *   <li>{@code FILTER_QUERY} — awaiting free-text client name from the seller</li>
 * </ul>
 *
 * Fallback: any unknown {@code FILTER_*} state is cleared and the seller is sent to the main menu.
 */
@Component
@RequiredArgsConstructor
public class DebtFilterConversationFlow implements ConversationFlow {

    private final DebtSearchService debtSearchService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("FILTER_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Long chatId = msg.getChatId();
        Lang lang = Lang.fromStored(user.getLanguage());

        // Guard: sellers only; if no shop, clear and bail
        if (user.getRole() != Role.SELLER || user.getShop() == null) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "err.no_shop_assigned"));
            return;
        }

        if ("FILTER_QUERY".equals(state)) {
            String query = (text == null) ? "" : text.trim();
            if (query.isEmpty()) {
                // Re-ask instead of bailing; user may have sent a non-text message
                messenger.send(chatId, loc.t(user, "filter.ask_name"));
                return;
            }

            var results = debtSearchService.filter(
                    user.getShop(),
                    new DebtSearchService.Filter(null, false, null, null, query)
            );

            userService.clearState(user.getTelegramId());

            if (results.isEmpty()) {
                messenger.send(chatId, loc.t(user, "filter.empty"));
            } else {
                messenger.sendMarkdown(chatId, MessageFormatter.formatDebtList(results, lang));
            }
            return;
        }

        // ── FALLBACK ─────────────────────────────────────────────────────────
        // Unknown FILTER_ state — unblock the user.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
    }
}
