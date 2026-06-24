package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.config.BotConfig;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.PaymentRequestService;
import com.qarzbot.service.ShopRequestService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientConversationFlow implements ConversationFlow {

    private final UserService userService;
    private final ShopService shopService;
    private final MembershipService membershipService;
    private final DebtService debtService;
    private final PaymentRequestService paymentRequestService;
    private final ShopRequestService shopRequestService;
    private final BotConfig botConfig;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("CLIENT_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Lang lang = Lang.fromStored(user.getLanguage());

        // CLIENT — do'kon qidirish
        if (state.equals("CLIENT_SHOP_SEARCH")) {
            String query = text.trim();
            userService.clearState(user.getTelegramId());
            List<Shop> shops = shopService.searchByNameOrId(query);
            if (shops.isEmpty()) {
                messenger.menu(user, msg.getChatId(), loc.t(user, "client.search_empty"));
                return;
            }
            for (Shop shop : shops) {
                String shopInfo = loc.t(user, "client.shop_line", shop.getName(), shop.getId())
                        + (shop.getAddress() != null ? loc.t(user, "client.shop_addr_line", shop.getAddress()) : "");
                boolean isMember = membershipService.isAcceptedMember(user, shop);
                if (isMember) {
                    messenger.execute(SendMessage.builder()
                            .chatId(msg.getChatId().toString())
                            .text(shopInfo + "\n" + loc.t(user, "client.shop_info_linked"))
                            .build());
                } else {
                    messenger.execute(SendMessage.builder()
                            .chatId(msg.getChatId().toString())
                            .text(shopInfo)
                            .replyMarkup(KeyboardFactory.singleInline(loc.t(user, "client.btn_link"), "link:" + shop.getId()))
                            .build());
                }
            }
            messenger.menu(user, msg.getChatId(), loc.t(user, "common.menu"));
            return;
        }

        // CLIENT — to'lov so'rovi summasi kiritish
        if (state.startsWith("CLIENT_PAY_AMOUNT:")) {
            Long debtId = Long.parseLong(state.substring("CLIENT_PAY_AMOUNT:".length()));
            BigDecimal amount;
            try {
                amount = new BigDecimal(text.replaceAll("\\s+", ""));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid"));
                return;
            }
            PaymentRequest req;
            try {
                req = paymentRequestService.create(debtId, amount);
            } catch (IllegalArgumentException e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage() + "\nQayta kiriting:");
                // state saqlanadi, foydalanuvchi qayta urinadi
                return;
            }
            userService.clearState(user.getTelegramId());
            messenger.menu(user, msg.getChatId(), loc.t(user, "client.payment_request_sent", MessageFormatter.money(amount, lang)));

            // Sotuvchilarga xabar (har bir sotuvchining tilida)
            Optional<Debt> debtOpt = debtService.findByIdFetched(debtId);
            if (debtOpt.isPresent()) {
                Debt debt = debtOpt.get();
                Shop shop = debt.getShop();
                String shopCurrency = shop != null ? shop.getCurrency() : null;
                List<BotUser> sellers = userService.findSellersByShop(shop);
                for (BotUser seller : sellers) {
                    try {
                        Lang sellerLang = Lang.fromStored(seller.getLanguage());
                        messenger.execute(SendMessage.builder()
                                .chatId(seller.getTelegramId().toString())
                                .text(loc.t(sellerLang, "notify.payment_request",
                                        user.getFullName(),
                                        debt.getId(),
                                        MessageFormatter.money(amount, shopCurrency, sellerLang),
                                        MessageFormatter.money(debt.getRemainingAmount(), shopCurrency, sellerLang)))
                                .replyMarkup(KeyboardFactory.acceptReject("payok:", "payno:", req.getId()))
                                .build());
                    } catch (Exception ignored) {}
                }
            }
            return;
        }

        // CLIENT — do'kon ochish: nom kiritish
        if (state.equals("CLIENT_OPENSHOP_NAME")) {
            String name = text.trim();
            if (name.isEmpty()) {
                messenger.replyMarkdown(msg, loc.t(user, "prompt.openshop_name"));
                return;
            }
            userService.setState(user.getTelegramId(), "CLIENT_OPENSHOP_ADDR:" + name);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.openshop_addr"));
            return;
        }

        // CLIENT — do'kon ochish: manzil kiritish
        if (state.startsWith("CLIENT_OPENSHOP_ADDR:")) {
            String name = state.substring("CLIENT_OPENSHOP_ADDR:".length());
            String address = "-".equals(text.trim()) ? null : text.trim();
            ShopRequest req = shopRequestService.create(user.getTelegramId(), name, address);
            userService.clearState(user.getTelegramId());
            messenger.menu(user, msg.getChatId(), loc.t(user, "client.openshop_sent"));
            String addrShown = address != null ? address : "—";
            String phoneShown = user.getPhoneNumber() != null ? user.getPhoneNumber() : "-";
            for (Long adminId : botConfig.getAdminIds()) {
                try {
                    // Adminning tilida xabar.
                    Lang adminLang = userService.findById(adminId)
                            .map(u -> Lang.fromStored(u.getLanguage()))
                            .orElse(Lang.UZ);
                    messenger.execute(SendMessage.builder()
                            .chatId(adminId.toString())
                            .text(loc.t(adminLang, "notify.shop_request",
                                    name, addrShown, user.getFullName(), phoneShown))
                            .replyMarkup(KeyboardFactory.acceptReject("shopreq_ok:", "shopreq_no:", req.getId()))
                            .build());
                } catch (Exception ignored) {}
            }
            return;
        }

        // Holat CLIENT_ bilan boshlanadi, lekin hech bir bosqichga mos kelmadi —
        // qotib qolmaslik uchun state'ni tozalab, asosiy menyuga qaytaramiz.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, msg.getChatId(), loc.t(user, "common.cancelled"));
    }
}
