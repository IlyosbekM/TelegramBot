package com.qarzbot.handler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.i18n.Menus;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClientHandler {

    private final DebtService debtService;
    private final PaymentRepository paymentRepository;
    private final MembershipService membershipService;
    private final ShopService shopService;
    private final UserService userService;
    private final BotMessenger messenger;
    private final HelpService helpService;
    private final Menus menus;
    private final Loc loc;

    public void handle(BotUser client, Message message) {
        Lang lang = Lang.fromStored(client.getLanguage());
        // RU/EN tugma matnlarini kanonik UZ matnga moslab, mavjud switch-case'ni o'zgartirmaymiz.
        String text = menus.canonical(message.getText());
        switch (text) {
            case "💳 Mening qarzlarim"  -> showMyDebts(message, client);
            case "📜 To'lov tarixim"    -> showPaymentHistory(message, client);
            case "🏪 Do'konlar"         -> showShops(message, client);
            case "🔍 Do'kon qidirish"   -> {
                userService.setState(client.getTelegramId(), "CLIENT_SHOP_SEARCH");
                messenger.execute(SendMessage.builder()
                        .chatId(message.getChatId().toString())
                        .text(loc.t(client, "prompt.shop_search"))
                        .replyMarkup(KeyboardFactory.cancel(lang))
                        .build());
            }
            case "🏬 Do'kon ochish" -> {
                userService.setState(client.getTelegramId(), "CLIENT_OPENSHOP_NAME");
                messenger.execute(SendMessage.builder()
                        .chatId(message.getChatId().toString())
                        .text(loc.t(client, "prompt.openshop_name"))
                        .replyMarkup(KeyboardFactory.cancel(lang))
                        .build());
            }
            case "ℹ️ Yordam"    -> messenger.replyMarkdown(message, helpService.clientHelp(lang));
            case "⚙️ Sozlamalar" -> showSettings(message, client);
            default -> messenger.reply(message, loc.t(client, "common.menu_select"));
        }
    }

    // Barcha do'konlarni ko'rsatish va bog'lanish imkoniyati
    private void showShops(Message m, BotUser client) {
        List<Shop> shops = shopService.findAll();
        if (shops.isEmpty()) {
            messenger.reply(m, loc.t(client, "client.no_shops"));
            return;
        }
        for (Shop shop : shops) {
            String shopInfo = loc.t(client, "client.shop_line", shop.getName(), shop.getId())
                    + (shop.getAddress() != null ? loc.t(client, "client.shop_addr_line", shop.getAddress()) : "");
            boolean isMember = membershipService.isAcceptedMember(client, shop);
            if (isMember) {
                messenger.execute(SendMessage.builder()
                        .chatId(m.getChatId().toString())
                        .text(shopInfo + "\n" + loc.t(client, "client.shop_info_linked"))
                        .build());
            } else {
                messenger.execute(SendMessage.builder()
                        .chatId(m.getChatId().toString())
                        .text(shopInfo)
                        .replyMarkup(KeyboardFactory.singleInline(loc.t(client, "client.btn_link"), "link:" + shop.getId()))
                        .build());
            }
        }
    }

    private void showMyDebts(Message m, BotUser client) {
        Lang lang = Lang.fromStored(client.getLanguage());
        List<Debt> debts = debtService.findActiveByClient(client);
        if (debts.isEmpty()) {
            messenger.reply(m, loc.t(client, "client.no_active_debts"));
            return;
        }

        // Do'kon bo'yicha guruhlash va umumiy qoldiqni hisoblash
        Map<String, BigDecimal> byShop = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Debt d : debts) {
            String shopName = d.getShop().getName();
            byShop.merge(shopName, d.getRemainingAmount(), BigDecimal::add);
            grandTotal = grandTotal.add(d.getRemainingAmount());
        }

        StringBuilder summary = new StringBuilder(loc.t(client, "client.my_debts_header")).append("\n\n");
        for (Map.Entry<String, BigDecimal> entry : byShop.entrySet()) {
            summary.append(loc.t(client, "client.my_debts_shop_line",
                            entry.getKey(), MessageFormatter.money(entry.getValue(), lang)))
                   .append("\n");
        }
        summary.append(loc.t(client, "client.debts_divider")).append("\n");
        summary.append(loc.t(client, "client.my_debts_total", MessageFormatter.money(grandTotal, lang)));

        messenger.execute(SendMessage.builder()
                .chatId(m.getChatId().toString())
                .text(summary.toString())
                .parseMode("Markdown")
                .build());

        // Har qarz alohida xabar (mavjud mantiq)
        for (Debt d : debts) {
            messenger.execute(SendMessage.builder()
                    .chatId(m.getChatId().toString())
                    .text(MessageFormatter.formatDebt(d, lang))
                    .parseMode("Markdown")
                    .replyMarkup(clientDebtButtons(d.getId(), client))
                    .build());
        }
    }

    private InlineKeyboardMarkup clientDebtButtons(Long debtId, BotUser client) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text(loc.t(client, "client.btn_pay")).callbackData("paydebt:" + debtId).build(),
                InlineKeyboardButton.builder().text(loc.t(client, "client.btn_payments")).callbackData("mypay:" + debtId).build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🧾 PDF").callbackData("pdf:debt:" + debtId).build(),
                InlineKeyboardButton.builder().text("📷 QR").callbackData("qr:debt:" + debtId).build()
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void showSettings(Message m, BotUser client) {
        Lang lang = Lang.fromStored(client.getLanguage());
        boolean enabled = client.getRemindersEnabled() == null || client.getRemindersEnabled();
        messenger.execute(SendMessage.builder()
                .chatId(m.getChatId().toString())
                .text(loc.t(client, "client.settings_text"))
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.clientSettings(enabled, lang))
                .build());
    }

    private void showPaymentHistory(Message m, BotUser client) {
        Lang lang = Lang.fromStored(client.getLanguage());
        List<Debt> debts = debtService.findAllByClient(client);
        if (debts.isEmpty()) {
            messenger.reply(m, loc.t(client, "client.payment_history_empty"));
            return;
        }
        StringBuilder sb = new StringBuilder(loc.t(client, "client.payment_history_header")).append("\n\n");
        boolean any = false;
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        for (Debt d : debts) {
            List<Payment> payments = paymentRepository.findByDebt(d);
            for (Payment p : payments) {
                sb.append(loc.t(client, "client.payment_history_item",
                                p.getPaidAt().format(df),
                                MessageFormatter.money(p.getAmount(), lang),
                                d.getShop().getName()))
                  .append("\n");
                any = true;
            }
        }
        if (!any) sb.append(loc.t(client, "client.payment_history_none"));
        messenger.execute(SendMessage.builder()
                .chatId(m.getChatId().toString())
                .text(sb.toString())
                .parseMode("Markdown")
                .build());
    }
}
