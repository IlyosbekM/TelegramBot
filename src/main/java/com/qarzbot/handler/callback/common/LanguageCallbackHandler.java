package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Til tanlash tugmalarini ("lang:uz" / "lang:ru" / "lang:en") ushlaydi.
 * Foydalanuvchi tilini yangilaydi, tasdiq beradi va yangi tildagi asosiy menyuni yuboradi.
 */
@Component
@RequiredArgsConstructor
public class LanguageCallbackHandler implements CallbackHandler {

    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data.startsWith("lang:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        String code = update.getCallbackQuery().getData().substring("lang:".length());
        Lang lang = Lang.fromStored(code);

        BotUser updated = userService.setLanguage(user.getTelegramId(), lang.getCode());

        String callbackQueryId = update.getCallbackQuery().getId();
        messenger.answerCallback(callbackQueryId, loc.t(updated, "lang.changed"));

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        messenger.menu(updated, chatId, loc.t(updated, "lang.changed"));
    }
}
