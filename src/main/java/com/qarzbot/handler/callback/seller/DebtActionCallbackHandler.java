package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DebtActionCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final DebtService debtService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("pay:") || data.startsWith("increase:") || data.startsWith("edit:")
                || data.startsWith("editamount:") || data.startsWith("editdesc:") || data.startsWith("editdue:")
                || data.startsWith("delete:") || data.startsWith("delyes:") || data.startsWith("delno:")
                || data.startsWith("history:") || data.startsWith("remind:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("pay:")) {
            Long debtId = Long.parseLong(data.substring(4));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            userService.setState(user.getTelegramId(), "SELLER_PAYMENT_AMOUNT:" + debtId);
            messenger.send(chatId, "Qarz #" + debtId + " uchun to'lov summasini kiriting:");

        } else if (data.startsWith("increase:")) {
            Long debtId = Long.parseLong(data.substring("increase:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            userService.setState(user.getTelegramId(), "SELLER_INCREASE_AMOUNT:" + debtId);
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Qarz #" + debtId + " ga qo'shiladigan summani kiriting:")
                    .replyMarkup(KeyboardFactory.cancel())
                    .build());

        } else if (data.startsWith("edit:")) {
            Long debtId = Long.parseLong(data.substring("edit:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Qarz #" + debtId + " — nimani tahrirlaysiz?")
                    .replyMarkup(KeyboardFactory.editMenu(debtId))
                    .build());

        } else if (data.startsWith("editamount:")) {
            Long debtId = Long.parseLong(data.substring("editamount:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            userService.setState(user.getTelegramId(), "SELLER_EDIT_AMOUNT:" + debtId);
            messenger.send(chatId, "Yangi umumiy summani kiriting:");

        } else if (data.startsWith("editdesc:")) {
            Long debtId = Long.parseLong(data.substring("editdesc:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            userService.setState(user.getTelegramId(), "SELLER_EDIT_DESC:" + debtId);
            messenger.send(chatId, "Yangi izohni kiriting (yoki '-' bo'sh qoldirish uchun):");

        } else if (data.startsWith("editdue:")) {
            Long debtId = Long.parseLong(data.substring("editdue:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            userService.setState(user.getTelegramId(), "SELLER_EDIT_DUE:" + debtId);
            messenger.send(chatId, "Yangi muddat (dd.MM.yyyy) yoki '-' (muddatsiz):");

        } else if (data.startsWith("delete:")) {
            Long debtId = Long.parseLong(data.substring("delete:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Qarz #" + debtId + " butunlay o'chiriladi (to'lov tarixi bilan). Tasdiqlaysizmi?")
                    .replyMarkup(KeyboardFactory.deleteConfirm(debtId))
                    .build());

        } else if (data.startsWith("delyes:")) {
            Long debtId = Long.parseLong(data.substring("delyes:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            try {
                debtService.delete(debtId);
                messenger.send(chatId, "✅ Qarz #" + debtId + " o'chirildi.");
                try {
                    auditService.log(user.getShop(), user.getTelegramId(), "DEBT_DELETE", debtId, null);
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ O'chirishda xatolik: " + e.getMessage());
            }

        } else if (data.startsWith("delno:")) {
            messenger.send(chatId, "Bekor qilindi.");

        } else if (data.startsWith("history:")) {
            Long debtId = Long.parseLong(data.substring("history:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            Optional<Debt> debtOpt = debtService.findById(debtId);
            if (debtOpt.isEmpty()) {
                messenger.send(chatId, "❌ Qarz topilmadi.");
                return;
            }
            List<Payment> payments = paymentRepository.findByDebt(debtOpt.get());
            if (payments.isEmpty()) {
                messenger.send(chatId, "Qarz #" + debtId + " uchun to'lovlar yo'q.");
                return;
            }
            DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            StringBuilder sb = new StringBuilder("*📜 Qarz #" + debtId + " to'lovlar tarixi:*\n\n");
            for (Payment p : payments) {
                sb.append("• ").append(p.getPaidAt().format(df))
                  .append(" — ").append(MessageFormatter.money(p.getAmount()));
                if (p.getNote() != null && !p.getNote().isBlank()) {
                    sb.append(" (").append(p.getNote()).append(")");
                }
                sb.append("\n");
            }
            messenger.sendMarkdown(chatId, sb.toString());

        } else if (data.startsWith("remind:")) {
            Long debtId = Long.parseLong(data.substring("remind:".length()));
            if (!checkDebtBelongsToShop(chatId, debtId, user)) return;
            Optional<Debt> debtOpt = debtService.findById(debtId);
            if (debtOpt.isEmpty()) {
                messenger.send(chatId, "❌ Qarz topilmadi.");
                return;
            }
            Debt debt = debtOpt.get();
            try {
                messenger.execute(SendMessage.builder()
                        .chatId(debt.getClient().getTelegramId().toString())
                        .text("⏰ *Eslatma:* Sizda *" + debt.getShop().getName() + "* do'konida "
                                + MessageFormatter.money(debt.getRemainingAmount()) + " qarz mavjud.\n"
                                + "Qarz #" + debt.getId())
                        .parseMode("Markdown")
                        .build());
                messenger.send(chatId, "✅ Eslatma yuborildi.");
            } catch (Exception e) {
                messenger.send(chatId, "❌ Eslatma yuborishda xatolik: " + e.getMessage());
            }
        }
    }

    // ── Xavfsizlik yordamchi metodi ──────────────────────────────────────────
    private boolean checkDebtBelongsToShop(Long chatId, Long debtId, BotUser user) {
        if (user.getShop() == null) {
            messenger.send(chatId, "❌ Bu sizning do'koningizga tegishli emas.");
            return false;
        }
        Optional<Debt> debtOpt = debtService.findByIdFetched(debtId);
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
