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

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductManageCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final ProductService productService;
    private final UserService userService;

    @Override
    public boolean supports(String data) {
        return data.equals("prodadd")
                || data.equals("proddelno")
                || data.startsWith("prodview:")
                || data.startsWith("prodedit:")
                || data.startsWith("proddel:")
                || data.startsWith("proddelyes:");
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

        if (data.equals("prodadd")) {
            userService.setState(user.getTelegramId(), "PROD_ADD_NAME");
            messenger.send(chatId, "🛒 Yangi mahsulot nomini kiriting:", KeyboardFactory.cancel());
            return;
        }

        if (data.equals("proddelno")) {
            messenger.send(chatId, "Bekor qilindi.");
            return;
        }

        if (data.startsWith("prodview:")) {
            Long productId = Long.parseLong(data.substring("prodview:".length()));
            if (!owned(chatId, productId, shop)) return;
            Product p = productService.findById(productId).get();
            String text = "🛒 *" + p.getName() + "*\n"
                    + "💰 Narx: " + MessageFormatter.money(p.getPrice()) + "\n"
                    + "📦 Birlik: " + (p.getUnit() != null ? p.getUnit() : "—") + "\n"
                    + "🔢 Ombor: " + (p.getStock() != null ? p.getStock() : "—");
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.productActions(productId))
                    .build());
            return;
        }

        if (data.startsWith("prodedit:")) {
            Long productId = Long.parseLong(data.substring("prodedit:".length()));
            if (!owned(chatId, productId, shop)) return;
            userService.setState(user.getTelegramId(), "PROD_EDIT_NAME:" + productId);
            messenger.send(chatId, "✏️ Yangi nom (yoki '-' — o'zgartirmaslik):", KeyboardFactory.cancel());
            return;
        }

        if (data.startsWith("proddel:")) {
            Long productId = Long.parseLong(data.substring("proddel:".length()));
            if (!owned(chatId, productId, shop)) return;
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ Mahsulot o'chirilsinmi?")
                    .replyMarkup(KeyboardFactory.productDeleteConfirm(productId))
                    .build());
            return;
        }

        if (data.startsWith("proddelyes:")) {
            Long productId = Long.parseLong(data.substring("proddelyes:".length()));
            if (!owned(chatId, productId, shop)) return;
            productService.delete(productId);
            messenger.send(chatId, "✅ Mahsulot o'chirildi.");
            messenger.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("🛒 *Mahsulotlar*")
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.sellerProductList(productService.findByShop(shop)))
                    .build());
        }
    }

    /**
     * Checks that the product exists and belongs to the given shop.
     * Sends an error message and returns false if ownership fails.
     */
    private boolean owned(Long chatId, Long productId, Shop shop) {
        Optional<Product> opt = productService.findById(productId);
        if (opt.isEmpty() || !opt.get().getShop().getId().equals(shop.getId())) {
            messenger.send(chatId, "❌ Mahsulot topilmadi.");
            return false;
        }
        return true;
    }
}
