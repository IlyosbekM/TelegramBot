package com.qarzbot.handler.callback.client;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ClientSettingsCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final UserService userService;

    @Override
    public boolean supports(String data) {
        return data.equals("remtoggle");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.CLIENT) return;

        boolean current = user.getRemindersEnabled() == null || user.getRemindersEnabled();
        boolean next = !current;

        userService.setRemindersEnabled(user.getTelegramId(), next);

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        try {
            messenger.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text("⚙️ *Sozlamalar*\n\n" + (next
                            ? "🔔 Avtomatik eslatmalar yoqildi."
                            : "🔕 Avtomatik eslatmalar o'chirildi."))
                    .parseMode("Markdown")
                    .replyMarkup(com.qarzbot.util.KeyboardFactory.clientSettings(next))
                    .build());
        } catch (Exception e) {
            // Xabar o'zgartirishda xato (masalan, xabar eskirib qolgan)
        }
    }
}
