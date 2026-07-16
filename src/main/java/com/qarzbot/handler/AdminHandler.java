package com.qarzbot.handler;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.i18n.Menus;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.AnalyticsService;
import com.qarzbot.handler.seller.AnalyticsPresenter;
import com.qarzbot.service.ShopRequestService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminHandler {

    private final ShopService shopService;
    private final DebtService debtService;
    private final UserService userService;
    private final ShopRequestService shopRequestService;
    private final HelpService helpService;
    private final BotMessenger messenger;
    private final Menus menus;
    private final Loc loc;
    private final AnalyticsService analyticsService;
    private final AnalyticsPresenter analyticsPresenter;

    public void handle(BotUser admin, Message message) {
        Lang lang = Lang.fromStored(admin.getLanguage());
        // RU/EN tugma matnlarini kanonik UZ matnga moslab, mavjud switch-case'ni o'zgartirmaymiz.
        String text = menus.canonical(message.getText());
        switch (text) {
            case "➕ Do'kon qo'shish" -> {
                userService.setState(admin.getTelegramId(), "ADMIN_ADD_SHOP_NAME");
                messenger.reply(message, loc.t(admin, "prompt.shop_name"));
            }
            case "🏪 Do'konlar" -> showShops(message, admin);
            case "📊 Umumiy hisobot" -> showOverallReport(message, admin);
            case "📈 Analitika" -> showAnalytics(message, admin);
            case "👥 Foydalanuvchilar" -> showUsers(message, admin);
            case "⚙️ Sozlamalar" -> showSettings(message, admin);
            case "ℹ️ Yordam" -> messenger.replyMarkdown(message, helpService.adminHelp(lang));
            case "📢 Xabar yuborish" -> messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(loc.t(admin, "admin.broadcast_target_ask"))
                    .replyMarkup(KeyboardFactory.broadcastTargets())
                    .build());
            default -> messenger.reply(message, loc.t(admin, "common.unknown_command"));
        }
    }

    private void showShops(Message message, BotUser admin) {
        Lang lang = Lang.fromStored(admin.getLanguage());
        List<Shop> shops = shopService.findAll();
        if (shops.isEmpty()) {
            messenger.reply(message, loc.t(admin, "admin.no_shops"));
            return;
        }
        StringBuilder sb = new StringBuilder(loc.t(admin, "admin.shops_header")).append("\n\n");
        for (Shop s : shops) {
            sb.append(loc.t(admin, "admin.shop_line", s.getName(), s.getId())).append("\n");
            if (s.getAddress() != null) sb.append(loc.t(admin, "admin.shop_addr_line", s.getAddress())).append("\n");
            BigDecimal total = debtService.totalActiveDebt(s);
            sb.append(loc.t(admin, "admin.shop_active_debt", MessageFormatter.money(total, lang))).append("\n\n");
        }
        messenger.replyMarkdown(message, sb.toString());
    }

    private void showOverallReport(Message message, BotUser admin) {
        Lang lang = Lang.fromStored(admin.getLanguage());
        List<Shop> shops = shopService.findAll();
        BigDecimal total = BigDecimal.ZERO;
        int active = 0;
        for (Shop s : shops) {
            total = total.add(debtService.totalActiveDebt(s));
            active += debtService.findActiveByShop(s).size();
        }
        String report = loc.t(admin, "admin.report", shops.size(), active, MessageFormatter.money(total, lang));
        messenger.replyMarkdown(message, report);
    }

    private void showAnalytics(Message message, BotUser admin) {
        Lang lang = Lang.fromStored(admin.getLanguage());
        var snapshot = analyticsService.forShops(shopService.findAll());
        messenger.replyMarkdown(message, analyticsPresenter.render(snapshot, lang));
    }

    private void showUsers(Message message, BotUser admin) {
        List<BotUser> allUsers = userService.findAll();
        if (allUsers.isEmpty()) {
            messenger.reply(message, loc.t(admin, "admin.no_users"));
            return;
        }

        List<BotUser> admins = allUsers.stream().filter(u -> u.getRole() == Role.ADMIN).toList();
        List<BotUser> sellers = allUsers.stream().filter(u -> u.getRole() == Role.SELLER).toList();
        List<BotUser> clients = allUsers.stream().filter(u -> u.getRole() == Role.CLIENT).toList();

        // Sarlavha
        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(loc.t(admin, "admin.users_header", admins.size(), sellers.size(), clients.size()))
                .parseMode("Markdown")
                .build());

        // Adminlar
        if (!admins.isEmpty()) {
            for (BotUser u : admins) {
                messenger.execute(SendMessage.builder()
                        .chatId(message.getChatId().toString())
                        .text(formatUserLine(u, loc.t(admin, "admin.role_admin")))
                        .parseMode("Markdown")
                        .build());
            }
        }

        // Sotuvchilar
        for (BotUser u : sellers) {
            String shopName = u.getShop() != null ? " (" + u.getShop().getName() + ")" : "";
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(formatUserLine(u, loc.t(admin, "admin.role_seller") + shopName))
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.singleInline(loc.t(admin, "admin.btn_make_client"), "mkclient:" + u.getTelegramId()))
                    .build());
        }

        // Klientlar
        for (BotUser u : clients) {
            messenger.execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text(formatUserLine(u, loc.t(admin, "admin.role_client")))
                    .parseMode("Markdown")
                    .replyMarkup(KeyboardFactory.singleInline(loc.t(admin, "admin.btn_make_seller"), "mkseller:" + u.getTelegramId()))
                    .build());
        }
    }

    private void showSettings(Message message, BotUser admin) {
        List<BotUser> allUsers = userService.findAll();
        List<Shop> allShops = shopService.findAll();
        long sellers = allUsers.stream().filter(u -> u.getRole() == Role.SELLER).count();
        long clients = allUsers.stream().filter(u -> u.getRole() == Role.CLIENT).count();

        String text = loc.t(admin, "admin.settings",
                admin.getTelegramId(),
                allUsers.size(),
                sellers,
                clients,
                allShops.size());
        messenger.execute(SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.adminSettingsActions())
                .build());
    }

    private String formatUserLine(BotUser u, String roleLabel) {
        String name = "*" + escapeMarkdown(u.getFullName()) + "*";
        String username = u.getUsername() != null ? " @" + u.getUsername() : "";
        String phone = u.getPhoneNumber() != null ? "\n📞 " + u.getPhoneNumber() : "";
        return roleLabel + "\n" + name + username + phone;
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[").replace("`", "\\`");
    }
}
