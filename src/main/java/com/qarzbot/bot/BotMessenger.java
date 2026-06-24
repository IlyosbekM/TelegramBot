package com.qarzbot.bot;

import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.BotUser;
import com.qarzbot.i18n.Lang;
import com.qarzbot.util.KeyboardFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Facade: barcha Telegram xabarlarini yuborishning yagona nuqtasi.
 * TelegramClient'ni shu klass egallaydi; QarzBot'ga bog'liq emas (circular dependency yo'q).
 */
@Slf4j
@Component
public class BotMessenger {

    private final TelegramClient client;

    public BotMessenger(BotConfig config) {
        this.client = new OkHttpTelegramClient(config.getToken());
    }

    /** Har qanday BotApiMethod (SendMessage, EditMessageReplyMarkup, ...) yuboradi. */
    public <T extends Serializable, M extends BotApiMethod<T>> void execute(M method) {
        try {
            client.execute(method);
        } catch (TelegramApiException e) {
            log.error("Telegramga yuborishda xato: {}", e.getMessage());
        }
    }

    public void send(Long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
    }

    public void sendMarkdown(Long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId.toString()).text(text).parseMode("Markdown").build());
    }

    public void send(Long chatId, String text, ReplyKeyboard markup) {
        execute(SendMessage.builder().chatId(chatId.toString()).text(text).replyMarkup(markup).build());
    }

    public void sendMarkdown(Long chatId, String text, ReplyKeyboard markup) {
        execute(SendMessage.builder().chatId(chatId.toString()).text(text).parseMode("Markdown").replyMarkup(markup).build());
    }

    public void reply(Message m, String text) {
        send(m.getChatId(), text);
    }

    public void replyMarkdown(Message m, String text) {
        sendMarkdown(m.getChatId(), text);
    }

    /** Asosiy menyu klaviaturasi bilan Markdown xabar (rol + foydalanuvchi tili bo'yicha). */
    public void menu(BotUser user, Long chatId, String text) {
        Lang lang = Lang.fromStored(user.getLanguage());
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.mainMenu(user.getRole(), lang))
                .build());
    }

    /**
     * Callback so'rovga javob beradi (Telegram'dagi "spinner"ni to'xtatadi).
     * showAlert=false; bo'sh matn bilan ham chaqirsa bo'ladi. Istisnolarni yutadi.
     */
    public void answerCallback(String callbackQueryId, String text) {
        try {
            client.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(false)
                    .build());
        } catch (Exception e) {
            log.warn("answerCallback xato: {}", e.getMessage());
        }
    }

    /** Bayt massividan hujjat (fayl) yuboradi. Istisnolarni yutadi. */
    public void sendDocument(Long chatId, byte[] content, String filename, String caption) {
        try {
            client.execute(SendDocument.builder()
                    .chatId(chatId.toString())
                    .document(new InputFile(new ByteArrayInputStream(content), filename))
                    .caption(caption)
                    .build());
        } catch (Exception e) {
            log.warn("sendDocument xato: {}", e.getMessage());
        }
    }

    /** Bayt massividan rasm yuboradi. Istisnolarni yutadi. */
    public void sendPhoto(Long chatId, byte[] content, String filename, String caption) {
        try {
            client.execute(SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new InputFile(new ByteArrayInputStream(content), filename))
                    .caption(caption)
                    .build());
        } catch (Exception e) {
            log.warn("sendPhoto xato: {}", e.getMessage());
        }
    }

    /** Inline so'rovga natijalar bilan javob beradi. Istisnolarni yutadi. */
    public void answerInlineQuery(String inlineQueryId, List<InlineQueryResult> results) {
        try {
            client.execute(AnswerInlineQuery.builder()
                    .inlineQueryId(inlineQueryId)
                    .results(results)
                    .cacheTime(1)
                    .build());
        } catch (Exception e) {
            log.warn("answerInlineQuery xato: {}", e.getMessage());
        }
    }

    /** Bir nechta foydalanuvchiga oddiy matnli (Markdown'siz) ommaviy xabar yuboradi. */
    public int broadcast(java.util.List<com.qarzbot.entity.BotUser> recipients, String header, String body) {
        int sent = 0;
        String full = header + "\n\n" + body;
        for (com.qarzbot.entity.BotUser r : recipients) {
            try {
                send(r.getTelegramId(), full);
                sent++;
            } catch (Exception ignored) {}
        }
        return sent;
    }
}
