package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.service.PdfReceiptService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

/**
 * PDF callback handler — "pdf:" prefiksini ushlaydi.
 *
 * Qo'llab-quvvatlangan ma'lumotlar:
 *   pdf:debt:<debtId>              — bitta qarz uchun PDF chek yuboradi
 *   pdf:statement:<clientTelegramId> — klientning barcha qarzlari bo'yicha PDF hisobot yuboradi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "pdf:";
    private static final String DEBT_CMD = "pdf:debt:";
    private static final String STATEMENT_CMD = "pdf:statement:";

    private final PdfReceiptService pdfReceiptService;
    private final DebtRepository debtRepository;
    private final UserService userService;
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
        Lang lang = Lang.fromStored(user.getLanguage());

        if (data.startsWith(DEBT_CMD)) {
            handleSingleDebt(data, chatId, callbackId, user, lang);
        } else if (data.startsWith(STATEMENT_CMD)) {
            handleStatement(data, chatId, callbackId, user, lang);
        } else {
            messenger.answerCallback(callbackId, "");
        }
    }

    // ── pdf:debt:<debtId> ────────────────────────────────────────────────────

    private void handleSingleDebt(String data, Long chatId, String callbackId,
                                   BotUser user, Lang lang) {
        Long debtId;
        try {
            debtId = Long.parseLong(data.substring(DEBT_CMD.length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackId, loc.t(user, "err.id_invalid"));
            return;
        }

        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        if (debtOpt.isEmpty()) {
            messenger.answerCallback(callbackId, loc.t(user, "err.debt_not_found_plain"));
            messenger.send(chatId, loc.t(user, "err.debt_not_found"));
            return;
        }

        messenger.answerCallback(callbackId, "");
        Debt debt = debtOpt.get();
        try {
            byte[] bytes = pdfReceiptService.singleDebtReceipt(debt, lang);
            String filename = "qarz-" + debtId + ".pdf";
            String caption = loc.t(user, "pdf.caption_debt");
            messenger.sendDocument(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("PDF chek yaratishda xato (qarz #{}): {}", debtId, e.getMessage(), e);
            messenger.send(chatId, loc.t(user, "err.internal_restart"));
        }
    }

    // ── pdf:statement:<clientTelegramId> ────────────────────────────────────

    private void handleStatement(String data, Long chatId, String callbackId,
                                  BotUser user, Lang lang) {
        Long clientTelegramId;
        try {
            clientTelegramId = Long.parseLong(data.substring(STATEMENT_CMD.length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackId, loc.t(user, "err.id_invalid"));
            return;
        }

        Optional<BotUser> clientOpt = userService.findById(clientTelegramId);
        if (clientOpt.isEmpty()) {
            messenger.answerCallback(callbackId, loc.t(user, "err.user_not_found"));
            messenger.send(chatId, loc.t(user, "err.user_not_found"));
            return;
        }

        messenger.answerCallback(callbackId, "");
        BotUser client = clientOpt.get();
        List<Debt> debts = debtRepository.findByClient(client);

        // Do'konni birinchi qarzdan olamiz (ro'yxat bo'sh bo'lishi mumkin)
        Shop shop = debts.isEmpty() ? null : debts.get(0).getShop();

        try {
            byte[] bytes = pdfReceiptService.debtStatement(client, shop, debts, lang);
            String filename = "hisobot-" + clientTelegramId + ".pdf";
            String caption = loc.t(user, "pdf.caption_statement");
            messenger.sendDocument(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("PDF hisobot yaratishda xato (klient #{}): {}", clientTelegramId, e.getMessage(), e);
            messenger.send(chatId, loc.t(user, "err.internal_restart"));
        }
    }
}
