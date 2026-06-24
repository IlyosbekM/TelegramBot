package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class MembershipCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final MembershipService membershipService;
    private final AuditService auditService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("mbacc:") || data.startsWith("mbrej:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("mbacc:")) {
            Long mbId = Long.parseLong(data.substring("mbacc:".length()));
            try {
                ShopMembership m = membershipService.accept(mbId);
                messenger.send(chatId, "✅ " + m.getClient().getFullName() + " qabul qilindi.");
                try {
                    messenger.execute(SendMessage.builder()
                            .chatId(m.getClient().getTelegramId().toString())
                            .text("✅ Siz " + m.getShop().getName() + " do'koniga bog'landingiz.")
                            .build());
                } catch (Exception ignored) {}
                try {
                    auditService.log(m.getShop(), user.getTelegramId(), "MEMBERSHIP_ACCEPT",
                            null, m.getClient().getFullName());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        } else if (data.startsWith("mbrej:")) {
            Long mbId = Long.parseLong(data.substring("mbrej:".length()));
            try {
                ShopMembership m = membershipService.reject(mbId);
                messenger.send(chatId, "❌ Rad etildi.");
                try {
                    messenger.execute(SendMessage.builder()
                            .chatId(m.getClient().getTelegramId().toString())
                            .text("❌ " + m.getShop().getName() + " bog'lanish so'rovingiz rad etildi.")
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        }
    }
}
