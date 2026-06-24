package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

/**
 * Callback handler for "qr:debt:<debtId>" prefix.
 * Generates a QR PNG encoding the debt summary and sends it as a photo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrCallbackHandler implements CallbackHandler {

    private final QrCodeService qrCodeService;
    private final DebtRepository debtRepository;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data.startsWith("qr:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        // qr:debt:<debtId>
        if (data.startsWith("qr:debt:")) {
            handleDebtQr(user, data, chatId);
        } else {
            messenger.send(chatId, loc.t(user, "err.internal_restart"));
        }
    }

    private void handleDebtQr(BotUser user, String data, Long chatId) {
        long debtId;
        try {
            debtId = Long.parseLong(data.substring("qr:debt:".length()));
        } catch (NumberFormatException e) {
            messenger.send(chatId, loc.t(user, "err.id_invalid"));
            return;
        }

        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        if (debtOpt.isEmpty()) {
            messenger.send(chatId, loc.t(user, "err.debt_not_found"));
            return;
        }

        Debt debt = debtOpt.get();

        try {
            String content = qrCodeService.buildDebtSummary(debt);
            byte[] bytes = qrCodeService.qrPng(content, 400);
            String caption = loc.t(user, "qr.caption_debt");
            String filename = "qr-" + debtId + ".png";
            messenger.sendPhoto(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("QR kod yuborishda xato (debtId={}): {}", debtId, e.getMessage());
            messenger.send(chatId, loc.t(user, "err.internal_restart"));
        }
    }
}
