package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.handler.seller.SellerViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class SellerClientViewCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final SellerViewService sellerViewService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("clientdebts:") || data.startsWith("statement:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("clientdebts:")) {
            Long clientId = Long.parseLong(data.substring("clientdebts:".length()));
            sellerViewService.showClientDebts(chatId, user, clientId);
        } else if (data.startsWith("statement:")) {
            Long clientTelegramId = Long.parseLong(data.substring("statement:".length()));
            sellerViewService.showStatement(chatId, user, clientTelegramId);
        }
    }
}
