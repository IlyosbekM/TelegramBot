package com.qarzbot.handler.callback.client;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.repository.PaymentRepository;
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
public class ClientPaymentCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final DebtService debtService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;

    @Override
    public boolean supports(String data) {
        return data.startsWith("paydebt:") || data.startsWith("mypay:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.CLIENT) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("paydebt:")) {
            Long debtId = Long.parseLong(data.substring("paydebt:".length()));
            Optional<Debt> debtOpt = debtService.findByIdFetched(debtId);
            if (debtOpt.isEmpty() || !debtOpt.get().getClient().getTelegramId().equals(user.getTelegramId())) {
                messenger.send(chatId, "❌ Qarz topilmadi.");
                return;
            }
            Debt debt = debtOpt.get();
            userService.setState(user.getTelegramId(), "CLIENT_PAY_AMOUNT:" + debtId);
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Qarz #" + debt.getId() + " — qoldiq: " + MessageFormatter.money(debt.getRemainingAmount())
                            + ".\nTo'lamoqchi summangizni kiriting:")
                    .replyMarkup(KeyboardFactory.cancel())
                    .build());

        } else if (data.startsWith("mypay:")) {
            Long debtId = Long.parseLong(data.substring("mypay:".length()));
            Optional<Debt> debtOpt = debtService.findByIdFetched(debtId);
            if (debtOpt.isEmpty() || !debtOpt.get().getClient().getTelegramId().equals(user.getTelegramId())) {
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
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(sb.toString())
                    .parseMode("Markdown")
                    .build());
        }
    }
}
