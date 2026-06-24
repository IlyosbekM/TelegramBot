package com.qarzbot.handler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Product;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.seller.SellerViewService;
import com.qarzbot.handler.seller.AnalyticsPresenter;
import com.qarzbot.service.AnalyticsService;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.i18n.Menus;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ProductService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SellerHandler {

    private final SellerViewService view;
    private final UserService userService;
    private final MembershipService membershipService;
    private final ProductService productService;
    private final BotMessenger messenger;
    private final HelpService helpService;
    private final Menus menus;
    private final Loc loc;
    private final AnalyticsService analyticsService;
    private final AnalyticsPresenter analyticsPresenter;

    public void handle(BotUser seller, Message message) {
        Lang lang = Lang.fromStored(seller.getLanguage());
        Shop shop = seller.getShop();
        if (shop == null) {
            messenger.reply(message, loc.t(seller, "seller.no_shop_contact_admin"));
            return;
        }

        // RU/EN tugma matnlarini kanonik UZ matnga moslab, mavjud switch-case'ni o'zgartirmaymiz.
        String text = menus.canonical(message.getText());
        switch (text) {
            case "➕ Qarz qo'shish"     -> view.showMembersForDebt(message, shop);
            case "📋 Qarzlar ro'yxati"  -> view.showActiveDebts(message, shop);
            case "🔍 Qidirish"          -> {
                userService.setState(seller.getTelegramId(), "SELLER_SEARCH");
                messenger.execute(SendMessage.builder()
                        .chatId(message.getChatId().toString())
                        .text(loc.t(seller, "prompt.search_client"))
                        .replyMarkup(KeyboardFactory.cancel(lang))
                        .build());
            }
            case "👥 Mijozlar"          -> view.showClients(message, shop);
            case "📊 Hisobot"           -> view.showReport(message, shop);
            case "📥 So'rovlar"         -> view.showRequests(message, shop);
            case "🧾 Tarix"             -> view.showAudit(message, shop);
            case "⏰ Eslatma yuborish"  -> view.sendReminders(message, shop);
            case "📈 Statistika"        -> view.showStatistics(message, shop);
            case "📈 Analitika"         -> showAnalytics(message, shop, seller);
            case "🛒 Mahsulotlar"       -> showProducts(message, shop, seller);
            case "ℹ️ Yordam"            -> messenger.replyMarkdown(message, helpService.sellerHelp(lang));
            case "📢 Xabar yuborish" -> {
                if (membershipService.acceptedMembers(shop).isEmpty()) {
                    messenger.reply(message, loc.t(seller, "seller.no_members"));
                } else {
                    userService.setState(seller.getTelegramId(), "SELLER_BCAST_TEXT");
                    messenger.execute(SendMessage.builder()
                            .chatId(message.getChatId().toString())
                            .text(loc.t(seller, "prompt.broadcast_text"))
                            .replyMarkup(KeyboardFactory.cancel(lang))
                            .build());
                }
            }
            default -> messenger.reply(message, loc.t(seller, "common.menu_select"));
        }
    }

    private void showAnalytics(Message message, Shop shop, BotUser seller) {
        Lang lang = Lang.fromStored(seller.getLanguage());
        var snapshot = analyticsService.forShop(shop);
        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(analyticsPresenter.render(snapshot, lang))
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.analyticsActions())
                .build());
    }

    private void showProducts(Message message, Shop shop, BotUser seller) {
        List<Product> products = productService.findByShop(shop);
        String text = products.isEmpty()
                ? loc.t(seller, "seller.products_empty")
                : loc.t(seller, "seller.products_list", products.size());
        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.sellerProductList(products))
                .build());
    }
}
