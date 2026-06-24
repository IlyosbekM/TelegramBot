package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Installment;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.service.InstallmentService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Callback handler for installment plan actions.
 * Prefix: {@code inst:}
 * Supported data patterns:
 *   inst:start:<debtId>   — begin creating an installment plan (set conversation state)
 *   inst:view:<debtId>    — display the current installment schedule
 *   inst:paid:<installmentId> — mark a single installment as paid
 */
@Component
@RequiredArgsConstructor
public class InstallmentCallbackHandler implements CallbackHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InstallmentService installmentService;
    private final DebtRepository debtRepository;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith("inst:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackQueryId = update.getCallbackQuery().getId();
        Lang lang = Lang.fromStored(user.getLanguage());

        // Only sellers may use these actions
        if (user.getRole() != Role.SELLER) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        if (data.startsWith("inst:start:")) {
            handleStart(user, chatId, callbackQueryId, data, lang);
        } else if (data.startsWith("inst:view:")) {
            handleView(user, chatId, callbackQueryId, data, lang);
        } else if (data.startsWith("inst:paid:")) {
            handleMarkPaid(user, chatId, callbackQueryId, data, lang);
        } else {
            messenger.answerCallback(callbackQueryId, "");
        }
    }

    // ── inst:start:<debtId> ─────────────────────────────────────────────────

    private void handleStart(BotUser user, Long chatId, String callbackQueryId,
                             String data, Lang lang) {
        Long debtId;
        try {
            debtId = Long.parseLong(data.substring("inst:start:".length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        // Verify the debt belongs to this seller's shop
        if (!debtBelongsToShop(chatId, debtId, user)) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        userService.setState(user.getTelegramId(), "INST_COUNT:" + debtId);
        messenger.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(loc.t(user, "inst.ask_count"))
                .replyMarkup(KeyboardFactory.cancel(lang))
                .build());
        messenger.answerCallback(callbackQueryId, "");
    }

    // ── inst:view:<debtId> ──────────────────────────────────────────────────

    private void handleView(BotUser user, Long chatId, String callbackQueryId,
                            String data, Lang lang) {
        Long debtId;
        try {
            debtId = Long.parseLong(data.substring("inst:view:".length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        List<Installment> schedule = installmentService.getSchedule(debtId);
        if (schedule.isEmpty()) {
            messenger.send(chatId, loc.t(user, "inst.schedule_empty"));
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        String text = buildScheduleText(schedule, user, lang);
        InlineKeyboardMarkup markup = buildScheduleKeyboard(schedule, lang);

        messenger.execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build());
        messenger.answerCallback(callbackQueryId, "");
    }

    // ── inst:paid:<installmentId> ───────────────────────────────────────────

    private void handleMarkPaid(BotUser user, Long chatId, String callbackQueryId,
                                String data, Lang lang) {
        Long installmentId;
        try {
            installmentId = Long.parseLong(data.substring("inst:paid:".length()));
        } catch (NumberFormatException e) {
            messenger.answerCallback(callbackQueryId, "");
            return;
        }

        try {
            Installment inst = installmentService.markPaid(installmentId);
            messenger.answerCallback(callbackQueryId, loc.t(user, "inst.paid_ok"));

            // Re-render the updated schedule
            List<Installment> schedule = installmentService.getSchedule(inst.getDebt().getId());
            String text = buildScheduleText(schedule, user, lang);
            InlineKeyboardMarkup markup = buildScheduleKeyboard(schedule, lang);

            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(markup)
                    .build());
        } catch (IllegalArgumentException e) {
            messenger.answerCallback(callbackQueryId, "");
            messenger.send(chatId, loc.t(user, "inst.err_not_found"));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String buildScheduleText(List<Installment> schedule, BotUser user, Lang lang) {
        Debt debt = schedule.get(0).getDebt();
        StringBuilder sb = new StringBuilder();
        sb.append(loc.t(user, "inst.schedule_title", debt.getId())).append("\n\n");
        for (Installment inst : schedule) {
            String marker = inst.isPaid() ? "✅" : "⏳";
            sb.append(marker)
              .append(" ").append(inst.getSeqNo()).append(". ")
              .append(inst.getDueDate().format(DATE_FMT))
              .append(" — ").append(MessageFormatter.money(inst.getAmount(), lang));
            if (inst.isPaid() && inst.getPaidDate() != null) {
                sb.append(" (").append(loc.t(user, "inst.paid_date", inst.getPaidDate().format(DATE_FMT))).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private InlineKeyboardMarkup buildScheduleKeyboard(List<Installment> schedule, Lang lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Installment inst : schedule) {
            if (!inst.isPaid()) {
                String label = "✅ " + inst.getSeqNo() + ". " + inst.getDueDate().format(DATE_FMT)
                        + " — " + MessageFormatter.money(inst.getAmount(), lang);
                InlineKeyboardButton btn = InlineKeyboardButton.builder()
                        .text(label)
                        .callbackData("inst:paid:" + inst.getId())
                        .build();
                rows.add(new InlineKeyboardRow(btn));
            }
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private boolean debtBelongsToShop(Long chatId, Long debtId, BotUser user) {
        if (user.getShop() == null) {
            messenger.send(chatId, "❌ Sizga do'kon biriktirilmagan.");
            return false;
        }
        Optional<Debt> debtOpt = debtRepository.findByIdFetched(debtId);
        if (debtOpt.isEmpty()) {
            messenger.send(chatId, "❌ Qarz topilmadi.");
            return false;
        }
        if (!debtOpt.get().getShop().getId().equals(user.getShop().getId())) {
            messenger.send(chatId, "❌ Bu sizning do'koningizga tegishli emas.");
            return false;
        }
        return true;
    }
}
