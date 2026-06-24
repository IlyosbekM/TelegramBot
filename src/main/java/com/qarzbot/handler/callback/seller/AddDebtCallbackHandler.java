package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Product;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ProductService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AddDebtCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final UserService userService;
    private final MembershipService membershipService;
    private final ProductService productService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("adddebt:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Shop shop = user.getShop();

        Long clientTelegramId = Long.parseLong(data.substring("adddebt:".length()));
        Optional<BotUser> clientOpt = userService.findById(clientTelegramId);
        if (clientOpt.isEmpty() || shop == null) {
            messenger.send(chatId, "❌ Mijoz topilmadi.");
            return;
        }
        BotUser client = clientOpt.get();
        if (!membershipService.isAcceptedMember(client, shop)) {
            messenger.send(chatId, "❌ Bu mijoz do'koningizga bog'lanmagan.");
            return;
        }
        List<Product> products = productService.findByShop(shop);
        if (products.isEmpty()) {
            userService.setState(user.getTelegramId(), "SELLER_ADD_DEBT_AMOUNT:" + clientTelegramId);
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Mijoz: " + client.getFullName() + "\nQarz summasini kiriting:")
                    .replyMarkup(KeyboardFactory.cancel())
                    .build());
        } else {
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Mijoz: " + client.getFullName() + "\nQarz qo'shish usulini tanlang:")
                    .replyMarkup(KeyboardFactory.addDebtMode(clientTelegramId))
                    .build());
        }
    }
}
