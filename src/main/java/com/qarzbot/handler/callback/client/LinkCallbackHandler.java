package com.qarzbot.handler.callback.client;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.MembershipService;
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
public class LinkCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final ShopService shopService;
    private final MembershipService membershipService;
    private final UserService userService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("link:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.CLIENT) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        Long shopId = Long.parseLong(data.substring("link:".length()));
        Shop shop = shopService.findById(shopId).orElse(null);
        if (shop == null) {
            messenger.send(chatId, "❌ Do'kon topilmadi.");
            return;
        }
        ShopMembership membership;
        try {
            membership = membershipService.request(user, shop);
        } catch (IllegalStateException e) {
            messenger.send(chatId, "⚠️ " + e.getMessage());
            return;
        }
        messenger.send(chatId, "✅ So'rov yuborildi. Sotuvchi tasdig'ini kuting.");
        List<BotUser> sellers = userService.findSellersByShop(shop);
        for (BotUser seller : sellers) {
            try {
                messenger.execute(SendMessage.builder()
                        .chatId(seller.getTelegramId().toString())
                        .text("🔔 Yangi bog'lanish so'rovi:\n"
                                + "👤 " + user.getFullName() + "\n"
                                + "📞 " + (user.getPhoneNumber() != null ? user.getPhoneNumber() : "—") + "\n"
                                + "🏪 " + shop.getName())
                        .replyMarkup(KeyboardFactory.acceptReject("mbacc:", "mbrej:", membership.getId()))
                        .build());
            } catch (Exception ignored) {}
        }
    }
}
