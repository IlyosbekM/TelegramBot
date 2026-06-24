package com.qarzbot.handler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.handler.conversation.ConversationFlow;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationHandler {

    private final List<ConversationFlow> flows;
    private final UserService userService;
    private final BotMessenger messenger;

    public void handle(BotUser user, Update update) {
        String state = user.getState();
        for (ConversationFlow f : flows) {
            if (f.supports(state)) {
                f.handle(user, update);
                return;
            }
        }
        messenger.replyMarkdown(update.getMessage(), "Bu buyruq tushunilmadi. /start orqali boshlang.");
        userService.clearState(user.getTelegramId());
    }

    // handleContact state'ga bog'liq emas — shu yerda qoladi.
    public void handleContact(BotUser user, Message msg) {
        Contact contact = msg.getContact();
        if (contact == null) return;
        String phone = contact.getPhoneNumber().startsWith("+") ? contact.getPhoneNumber() : "+" + contact.getPhoneNumber();
        user.setPhoneNumber(phone);
        userService.save(user);
        messenger.send(msg.getChatId(), "✅ Telefon raqamingiz saqlandi: " + phone);
        messenger.execute(SendMessage.builder()
                .chatId(msg.getChatId().toString())
                .text("Asosiy menyu:")
                .replyMarkup(KeyboardFactory.mainMenu(user.getRole()))
                .build());
    }
}
