package com.qarzbot.handler.callback.admin;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;

/**
 * Backup callback handler — "backup:" prefiksini ushlaydi.
 *
 * Qo'llab-quvvatlangan ma'lumot:
 *   backup:run — to'liq tizim zaxira nusxasini (JSON, ZIP) yaratib yuboradi (faqat ADMIN)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "backup:";

    private final BackupService backupService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(BotUser user, Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackId = update.getCallbackQuery().getId();

        if (!"backup:run".equals(data)) {
            messenger.answerCallback(callbackId, "");
            return;
        }

        if (user.getRole() != Role.ADMIN) {
            messenger.answerCallback(callbackId, "");
            return;
        }

        messenger.answerCallback(callbackId, "");
        try {
            byte[] bytes = backupService.fullBackupZip();
            String filename = "qarzbot_backup_" + LocalDate.now() + ".zip";
            String caption = loc.t(user, "backup.caption");
            messenger.sendDocument(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("Zaxira nusxa yaratishda xato: {}", e.getMessage(), e);
            messenger.send(chatId, loc.t(user, "backup.err"));
        }
    }
}
