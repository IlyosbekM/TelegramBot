package com.qarzbot.handler.callback.admin;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleChangeCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final ShopService shopService;
    private final UserService userService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("mkseller:") || data.startsWith("setseller:") || data.startsWith("mkclient:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.ADMIN) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("mkseller:")) {
            Long userId = Long.parseLong(data.substring("mkseller:".length()));
            List<Shop> shops = shopService.findAll();
            if (shops.isEmpty()) {
                messenger.send(chatId, "Avval do'kon qo'shing. \"➕ Do'kon qo'shish\" tugmasini bosing.");
                return;
            }
            messenger.send(chatId, "Qaysi do'konga sotuvchi qilish?",
                    KeyboardFactory.shopList(shops, userId));
        } else if (data.startsWith("setseller:")) {
            String[] parts = data.substring("setseller:".length()).split(":");
            Long userId = Long.parseLong(parts[0]);
            Long shopId = Long.parseLong(parts[1]);
            Shop shop = shopService.findById(shopId).orElse(null);
            if (shop == null) {
                messenger.send(chatId, "❌ Do'kon topilmadi.");
                return;
            }
            BotUser seller = userService.assignSeller(userId, shop);
            messenger.send(chatId, "✅ " + seller.getFullName() + " endi " + shop.getName() + " sotuvchisi.");
            // Yangi sotuvchiga xabar yuborish
            try {
                messenger.execute(SendMessage.builder()
                        .chatId(seller.getTelegramId().toString())
                        .text("🎉 Siz *" + shop.getName() + "* do'koniga sotuvchi etib tayinlandingiz.\n/start bosing va menyuni yangilang.")
                        .parseMode("Markdown")
                        .build());
            } catch (Exception ignored) {}
        } else if (data.startsWith("mkclient:")) {
            Long userId = Long.parseLong(data.substring("mkclient:".length()));
            try {
                BotUser demoted = userService.demoteToClient(userId);
                messenger.send(chatId, "✅ " + demoted.getFullName() + " endi klient sifatida ro'yxatda.");
                try {
                    messenger.execute(SendMessage.builder()
                            .chatId(demoted.getTelegramId().toString())
                            .text("Sizning rolingiz klientga o'zgartirildi. /start bosing.")
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.send(chatId, "❌ " + e.getMessage());
            }
        }
    }
}
