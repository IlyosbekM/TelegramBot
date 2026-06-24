package com.qarzbot.handler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackDispatcher;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.i18n.Menus;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@RequiredArgsConstructor
public class UpdateRouter {

    private final UserService userService;
    private final AdminHandler adminHandler;
    private final SellerHandler sellerHandler;
    private final ClientHandler clientHandler;
    private final ConversationHandler conversationHandler;
    private final CallbackDispatcher callbackDispatcher;
    private final BotMessenger messenger;
    private final Menus menus;
    private final HelpService helpService;
    private final Loc loc;

    public void route(Update update) {
        User tgUser = extractUser(update);
        if (tgUser == null) return;

        BotUser user = userService.registerIfAbsent(tgUser);

        // "❌ Bekor qilish" (har qanday tilda) — state bor-yo'qligidan qat'i nazar
        // har doim asosiy menyuga qaytaradi. (Aks holda oqim tugagach cancel
        // klaviaturasi qolib ketib, "Menyudan tanlang" loop bo'lardi.)
        if (update.hasMessage() && update.getMessage().hasText()
                && menus.isCancel(update.getMessage().getText())) {
            userService.clearState(user.getTelegramId());
            sendMainMenu(update.getMessage().getChatId(), user);
            return;
        }

        // Conversation state mavjud bo'lsa, avval shunga yo'naltirish
        if (user.getState() != null && !user.getState().isBlank() && update.hasMessage() && update.getMessage().hasText()) {
            conversationHandler.handle(user, update);
            return;
        }

        if (update.hasMessage()) {
            handleMessage(user, update.getMessage());
        } else if (update.hasCallbackQuery()) {
            callbackDispatcher.dispatch(user, update);
        }
    }

    private void handleMessage(BotUser user, Message message) {
        String text = message.getText();
        if (text == null) {
            if (message.hasContact()) {
                conversationHandler.handleContact(user, message);
            }
            return;
        }

        if ("/start".equals(text)) {
            sendMainMenu(message.getChatId(), user);
            return;
        }
        if ("/help".equals(text) || menus.isHelp(text)) {
            sendHelp(message.getChatId(), user);
            return;
        }
        if ("/language".equals(text) || "/til".equals(text) || "/lang".equals(text)) {
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(loc.t(user, "lang.choose"))
                    .replyMarkup(KeyboardFactory.languageMenu())
                    .build());
            return;
        }

        switch (user.getRole()) {
            case ADMIN -> adminHandler.handle(user, message);
            case SELLER -> sellerHandler.handle(user, message);
            case CLIENT -> clientHandler.handle(user, message);
        }
    }

    private void sendMainMenu(Long chatId, BotUser user) {
        Lang lang = Lang.fromStored(user.getLanguage());
        // CLIENT uchun telefon raqami yo'q bo'lsa, avval so'raymiz
        if (user.getRole() == Role.CLIENT
                && (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank())) {
            SendMessage ask = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(loc.t(lang, "contact.ask"))
                    .replyMarkup(KeyboardFactory.contactRequest(lang))
                    .build();
            messenger.execute(ask);
            return;
        }
        String greeting = switch (user.getRole()) {
            case CLIENT -> loc.t(lang, "greeting.client", user.getFullName());
            case SELLER -> loc.t(lang, "greeting.seller", user.getFullName());
            case ADMIN -> loc.t(lang, "greeting.admin", user.getFullName());
        };
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(greeting)
                .replyMarkup(KeyboardFactory.mainMenu(user.getRole(), lang))
                .build();
        messenger.execute(msg);
    }

    private void sendHelp(Long chatId, BotUser user) {
        Lang lang = Lang.fromStored(user.getLanguage());
        String help = helpService.help(user.getRole(), lang);
        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(help)
                .parseMode("Markdown")
                .build();
        messenger.execute(msg);
    }

    private User extractUser(Update update) {
        if (update.hasMessage()) return update.getMessage().getFrom();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getFrom();
        return null;
    }
}
