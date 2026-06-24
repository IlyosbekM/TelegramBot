package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Role;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.PaymentRequestService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRequestCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final PaymentRequestService paymentRequestService;
    private final DebtService debtService;
    private final AuditService auditService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("payok:") || data.startsWith("payno:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("payok:")) {
            Long reqId = Long.parseLong(data.substring("payok:".length()));
            // Oldin req ma'lumotini olamiz (confirm dan oldin)
            Optional<PaymentRequest> reqOpt = paymentRequestService.findByIdFetched(reqId);
            if (reqOpt.isEmpty()) {
                messenger.send(chatId, "❌ So'rov topilmadi.");
                return;
            }
            PaymentRequest preReq = reqOpt.get();
            Debt debt = preReq.getDebt();
            BigDecimal amount = preReq.getAmount();
            try {
                paymentRequestService.confirm(reqId, user.getTelegramId());
                messenger.send(chatId, "✅ To'lov tasdiqlandi.");
                // Yangilangan qarzni olamiz
                Optional<Debt> updatedDebtOpt = debtService.findByIdFetched(debt.getId());
                try {
                    String clientText = updatedDebtOpt.isPresent()
                            ? MessageFormatter.formatPaymentReceipt(updatedDebtOpt.get(), amount)
                            : "✅ " + MessageFormatter.money(amount) + " to'lovingiz tasdiqlandi. "
                                    + "Yangi qoldiq: " + MessageFormatter.money(debt.getRemainingAmount());
                    SendMessage.SendMessageBuilder clientMsg = SendMessage.builder()
                            .chatId(debt.getClient().getTelegramId().toString())
                            .text(clientText);
                    if (updatedDebtOpt.isPresent()) {
                        clientMsg.parseMode("Markdown");
                    }
                    messenger.execute(clientMsg.build());
                } catch (Exception ignored) {}
                try {
                    auditService.log(debt.getShop(), user.getTelegramId(), "PAYMENT_CONFIRM",
                            debt.getId(), MessageFormatter.money(amount));
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        } else if (data.startsWith("payno:")) {
            Long reqId = Long.parseLong(data.substring("payno:".length()));
            try {
                PaymentRequest req = paymentRequestService.reject(reqId);
                messenger.send(chatId, "❌ To'lov rad etildi.");
                try {
                    messenger.execute(SendMessage.builder()
                            .chatId(req.getDebt().getClient().getTelegramId().toString())
                            .text("❌ To'lov so'rovingiz rad etildi.")
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        }
    }
}
