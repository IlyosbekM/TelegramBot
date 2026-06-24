package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.DebtSearchService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Handles quick-filter callback buttons for sellers.
 * Prefix: {@code filter:}
 *
 * Supported actions:
 * <ul>
 *   <li>{@code filter:active}  — all active debts</li>
 *   <li>{@code filter:overdue} — overdue active debts (dueDate &lt; today)</li>
 *   <li>{@code filter:big}     — active debts sorted by remaining amount desc</li>
 *   <li>{@code filter:search}  — start a name-query conversation (state FILTER_QUERY)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DebtFilterCallbackHandler implements CallbackHandler {

    private final DebtSearchService debtSearchService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("filter:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        if (user.getShop() == null) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            messenger.send(chatId, loc.t(user, "err.no_shop_assigned"));
            return;
        }

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Lang lang = Lang.fromStored(user.getLanguage());

        switch (data) {
            case "filter:active" -> {
                List<Debt> debts = debtSearchService.quickActive(user.getShop());
                sendResults(chatId, debts, lang, user);
            }
            case "filter:overdue" -> {
                List<Debt> debts = debtSearchService.quickOverdue(user.getShop());
                sendResults(chatId, debts, lang, user);
            }
            case "filter:big" -> {
                List<Debt> debts = debtSearchService.quickBig(user.getShop());
                sendResults(chatId, debts, lang, user);
            }
            case "filter:search" -> {
                userService.setState(user.getTelegramId(), "FILTER_QUERY");
                messenger.send(chatId, loc.t(user, "filter.ask_name"), KeyboardFactory.cancel(lang));
            }
            default -> {
                // Unknown filter: ignore silently
            }
        }
    }

    private void sendResults(Long chatId, List<Debt> debts, Lang lang, BotUser user) {
        if (debts.isEmpty()) {
            messenger.send(chatId, loc.t(user, "filter.empty"));
        } else {
            messenger.sendMarkdown(chatId, MessageFormatter.formatDebtList(debts, lang));
        }
    }
}
