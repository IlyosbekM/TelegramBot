package com.qarzbot.handler.callback;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.i18n.Loc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/** Chain of Responsibility: birinchi mos keluvchi CallbackHandler ishlaydi. */
@Component
@RequiredArgsConstructor
public class CallbackDispatcher {
    private final List<CallbackHandler> handlers;
    private final BotMessenger messenger;
    private final Loc loc;

    public void dispatch(BotUser user, Update update) {
        String callbackQueryId = update.getCallbackQuery().getId();
        String data = update.getCallbackQuery().getData();
        if (data == null) {
            // Spinner'ni baribir to'xtatamiz.
            messenger.answerCallback(callbackQueryId, "");
            return;
        }
        for (CallbackHandler h : handlers) {
            if (h.supports(data)) {
                h.handle(user, update);
                // Handler ishladi — Telegram spinner'ini har doim to'xtatamiz.
                messenger.answerCallback(callbackQueryId, "");
                return;
            }
        }
        // Hech qaysi handler mos kelmadi (eskirgan tugma) — qisqa toast bilan javob.
        messenger.answerCallback(callbackQueryId, loc.t(user, "common.menu"));
    }
}
