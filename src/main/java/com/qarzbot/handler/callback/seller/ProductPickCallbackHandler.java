package com.qarzbot.handler.callback.seller;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Product;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.service.ProductService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductPickCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final UserService userService;
    private final ProductService productService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("dprod:") || data.startsWith("dmanual:") || data.startsWith("dpick:");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.SELLER) return;

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Shop shop = user.getShop();

        if (shop == null) {
            messenger.send(chatId, "❌ Sizga do'kon biriktirilmagan.");
            return;
        }

        if (data.startsWith("dmanual:")) {
            Long clientId = Long.parseLong(data.substring("dmanual:".length()));
            userService.setState(user.getTelegramId(), "SELLER_ADD_DEBT_AMOUNT:" + clientId);
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Qarz summasini kiriting:")
                    .replyMarkup(KeyboardFactory.cancel())
                    .build());

        } else if (data.startsWith("dprod:")) {
            Long clientId = Long.parseLong(data.substring("dprod:".length()));
            List<Product> products = productService.findByShop(shop);
            if (products.isEmpty()) {
                userService.setState(user.getTelegramId(), "SELLER_ADD_DEBT_AMOUNT:" + clientId);
                messenger.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("Qarz summasini kiriting:")
                        .replyMarkup(KeyboardFactory.cancel())
                        .build());
            } else {
                messenger.execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text("🛒 Mahsulotni tanlang:")
                        .replyMarkup(KeyboardFactory.debtProductList(products, clientId))
                        .build());
            }

        } else if (data.startsWith("dpick:")) {
            // format: dpick:<clientId>:<productId>
            String payload = data.substring("dpick:".length());
            String[] parts = payload.split(":");
            Long clientId = Long.parseLong(parts[0]);
            Long productId = Long.parseLong(parts[1]);

            Optional<Product> prodOpt = productService.findById(productId);
            if (prodOpt.isEmpty() || !prodOpt.get().getShop().getId().equals(shop.getId())) {
                messenger.send(chatId, "❌ Mahsulot topilmadi.");
                return;
            }
            Product product = prodOpt.get();
            userService.setState(user.getTelegramId(), "SELLER_ADD_DEBT_QTY:" + clientId + ":" + productId);
            String unitPart = product.getUnit() != null && !product.getUnit().isBlank()
                    ? " " + product.getUnit()
                    : "";
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(product.getName() + " — " + MessageFormatter.money(product.getPrice()) + ".\n"
                            + "Nechta/qancha? (masalan: 2):")
                    .replyMarkup(KeyboardFactory.cancel())
                    .build());
        }
    }
}
