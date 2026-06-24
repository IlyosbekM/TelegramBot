package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Installment;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.InstallmentService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation flow for installment plan creation.
 * State prefix: {@code INST_}
 *
 * States:
 *   INST_COUNT:<debtId>           — waiting for the user to enter the number of installments
 *   INST_PERIOD:<debtId>:<count>  — waiting for the user to enter the period in days
 */
@Component
@RequiredArgsConstructor
public class InstallmentConversationFlow implements ConversationFlow {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InstallmentService installmentService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("INST_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Long chatId = msg.getChatId();
        Lang lang = Lang.fromStored(user.getLanguage());

        // ── INST_COUNT:<debtId> ──────────────────────────────────────────────
        if (state.startsWith("INST_COUNT:")) {
            String debtIdStr = state.substring("INST_COUNT:".length());
            int count;
            try {
                count = Integer.parseInt(text.trim());
                if (count < 2 || count > 24) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.send(chatId, loc.t(user, "inst.err_count"), KeyboardFactory.cancel(lang));
                return;
            }
            userService.setState(user.getTelegramId(), "INST_PERIOD:" + debtIdStr + ":" + count);
            messenger.send(chatId, loc.t(user, "inst.ask_period"), KeyboardFactory.cancel(lang));
            return;
        }

        // ── INST_PERIOD:<debtId>:<count> ─────────────────────────────────────
        if (state.startsWith("INST_PERIOD:")) {
            // payload = debtId:count  — split at LAST colon so debtId (which is just a number) is safe
            String payload = state.substring("INST_PERIOD:".length());
            int lastColon = payload.lastIndexOf(':');
            if (lastColon < 0) {
                // Malformed state — fallback
                userService.clearState(user.getTelegramId());
                messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
                return;
            }
            String debtIdStr = payload.substring(0, lastColon);
            String countStr = payload.substring(lastColon + 1);

            Long debtId;
            int count;
            try {
                debtId = Long.parseLong(debtIdStr);
                count = Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                userService.clearState(user.getTelegramId());
                messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
                return;
            }

            int periodDays;
            try {
                periodDays = Integer.parseInt(text.trim());
                if (periodDays < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.send(chatId, loc.t(user, "inst.err_period"), KeyboardFactory.cancel(lang));
                return;
            }

            try {
                List<Installment> schedule = installmentService.createSchedule(debtId, count, periodDays);
                userService.clearState(user.getTelegramId());

                // Success message
                messenger.send(chatId, loc.t(user, "inst.created", count));

                // Render schedule with inline "mark paid" buttons
                String scheduleText = buildScheduleText(schedule, user, lang);
                InlineKeyboardMarkup markup = buildScheduleKeyboard(schedule, lang);
                messenger.execute(
                        SendMessage.builder()
                                .chatId(chatId.toString())
                                .text(scheduleText)
                                .parseMode("Markdown")
                                .replyMarkup(markup)
                                .build()
                );
            } catch (IllegalArgumentException e) {
                // e.getMessage() is a key like "inst.err_count" or "inst.err_period"
                messenger.send(chatId, loc.t(user, e.getMessage()), KeyboardFactory.cancel(lang));
            } catch (Exception e) {
                userService.clearState(user.getTelegramId());
                messenger.menu(user, chatId, loc.t(user, "err.internal_restart"));
            }
            return;
        }

        // ── FALLBACK: unknown INST_ state ────────────────────────────────────
        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "common.menu"));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String buildScheduleText(List<Installment> schedule, BotUser user, Lang lang) {
        Debt debt = schedule.get(0).getDebt();
        StringBuilder sb = new StringBuilder();
        sb.append(loc.t(user, "inst.schedule_title", debt.getId())).append("\n\n");
        for (Installment inst : schedule) {
            String marker = inst.isPaid() ? "✅" : "⏳";
            sb.append(marker)
              .append(" ").append(inst.getSeqNo()).append(". ")
              .append(inst.getDueDate().format(DATE_FMT))
              .append(" — ").append(MessageFormatter.money(inst.getAmount(), lang))
              .append("\n");
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
}
