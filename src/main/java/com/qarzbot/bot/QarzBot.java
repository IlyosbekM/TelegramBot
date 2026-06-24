package com.qarzbot.bot;

import com.qarzbot.config.BotConfig;
import com.qarzbot.handler.UpdateRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * QarzBot — Telegram long-polling boti.
 *
 * TelegramBots 10.0.0 yangi API:
 *  - {@link SpringLongPollingBot} — Spring Boot starter avtomatik ro'yxatga oladi
 *  - {@link LongPollingSingleThreadUpdateConsumer} — har bir update bitta thread'da qayta ishlanadi
 *  - Xabar yuborish {@link BotMessenger} orqali amalga oshiriladi (circular dependency yo'q)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QarzBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final BotConfig config;
    private final UpdateRouter router;
    private final BotMessenger messenger;

    @PostConstruct
    void init() {
        log.info("QarzBot ishga tushdi: @{}", config.getUsername());
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            router.route(update);
        } catch (Exception e) {
            log.error("Update qayta ishlashda xato: {}", e.getMessage(), e);
            sendError(update, "❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }

    private void sendError(Update update, String text) {
        Long chatId = update.hasMessage() ? update.getMessage().getChatId()
                : update.hasCallbackQuery() ? update.getCallbackQuery().getMessage().getChatId() : null;
        if (chatId == null) return;
        messenger.send(chatId, text);
    }
}
