package com.qarzbot.handler.callback;

import com.qarzbot.entity.BotUser;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CallbackHandler {
    boolean supports(String data);
    void handle(BotUser user, Update update);
}
