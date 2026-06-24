package com.qarzbot.handler.callback.admin;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.ShopRequestService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ShopRequestCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final ShopRequestService shopRequestService;
    private final UserService userService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("shopreq_ok:") || data.startsWith("shopreq_no:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.ADMIN) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("shopreq_ok:")) {
            Long id = Long.parseLong(data.substring("shopreq_ok:".length()));
            ShopRequest req = shopRequestService.findById(id).orElse(null);
            if (req == null || req.getStatus() != ShopRequest.Status.PENDING) {
                messenger.send(chatId, "❌ So'rov topilmadi yoki allaqachon ko'rib chiqilgan.");
                return;
            }
            try {
                Shop shop = shopRequestService.approve(id);
                messenger.send(chatId, "✅ Tasdiqlandi: " + shop.getName());
                try {
                    messenger.execute(SendMessage.builder()
                            .chatId(req.getRequesterTelegramId().toString())
                            .text("🎉 Do'kon ochish so'rovingiz tasdiqlandi! Siz \"" + shop.getName()
                                    + "\" do'koniga sotuvchi etib tayinlandingiz. /start bosing.")
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        } else if (data.startsWith("shopreq_no:")) {
            Long id = Long.parseLong(data.substring("shopreq_no:".length()));
            userService.setState(user.getTelegramId(), "ADMIN_SHOPREQ_REASON:" + id);
            messenger.send(chatId, "Rad etish sababini kiriting yoki '-' (sababsiz):");
        }
    }
}
