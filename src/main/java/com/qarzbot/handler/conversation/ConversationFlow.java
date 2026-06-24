package com.qarzbot.handler.conversation;

import com.qarzbot.entity.BotUser;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface ConversationFlow {
    boolean supports(String state);
    void handle(BotUser user, Update update);
}
