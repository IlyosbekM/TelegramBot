package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopRequestService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminConversationFlow implements ConversationFlow {

    private final UserService userService;
    private final ShopService shopService;
    private final MembershipService membershipService;
    private final ShopRequestService shopRequestService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("ADMIN_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();

        // ADMIN — yangi do'kon qo'shish
        if (state.startsWith("ADMIN_ADD_SHOP_NAME")) {
            user.setState("ADMIN_ADD_SHOP_ADDR:" + text);
            userService.save(user);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.shop_addr"));
            return;
        }
        if (state.startsWith("ADMIN_ADD_SHOP_ADDR:")) {
            String name = state.substring("ADMIN_ADD_SHOP_ADDR:".length());
            String addr = "-".equals(text) ? null : text;
            Shop shop = shopService.create(name, addr, null, user.getTelegramId());
            userService.clearState(user.getTelegramId());
            messenger.menu(user, msg.getChatId(), loc.t(user, "admin.shop_created", shop.getName(), shop.getId()));
            return;
        }

        // ADMIN — do'kon rad etish sababi
        if (state.startsWith("ADMIN_SHOPREQ_REASON:")) {
            Long reqId = Long.parseLong(state.substring("ADMIN_SHOPREQ_REASON:".length()));
            String reason = "-".equals(text.trim()) ? null : text.trim();
            ShopRequest r = shopRequestService.reject(reqId, reason);
            userService.clearState(user.getTelegramId());
            try {
                // Qabul qiluvchining (so'rovchi) tilida xabar yuboriladi.
                Lang reqLang = userService.findById(r.getRequesterTelegramId())
                        .map(u -> Lang.fromStored(u.getLanguage()))
                        .orElse(Lang.UZ);
                String body = loc.t(reqLang, "notify.shop_request_rejected")
                        + (reason != null ? loc.t(reqLang, "notify.shop_request_reject_reason", reason) : "");
                messenger.execute(SendMessage.builder()
                        .chatId(r.getRequesterTelegramId().toString())
                        .text(body)
                        .build());
            } catch (Exception ignored) {}
            messenger.menu(user, msg.getChatId(), loc.t(user, "admin.shopreq_rejected"));
            return;
        }

        // ADMIN — broadcast text handler
        if (state.startsWith("ADMIN_BCAST_TEXT:")) {
            String target = state.substring("ADMIN_BCAST_TEXT:".length());
            String body = text;
            List<BotUser> recipients = new ArrayList<>();
            if (target.equals("all")) {
                userService.findAll().stream()
                        .filter(u -> !u.getTelegramId().equals(user.getTelegramId()))
                        .forEach(recipients::add);
            } else if (target.startsWith("shop:")) {
                Long shopId = Long.parseLong(target.substring("shop:".length()));
                Shop shop = shopService.findById(shopId).orElse(null);
                if (shop == null) {
                    userService.clearState(user.getTelegramId());
                    messenger.menu(user, msg.getChatId(), loc.t(user, "err.shop_not_found"));
                    return;
                }
                java.util.LinkedHashMap<Long, BotUser> map = new java.util.LinkedHashMap<>();
                for (var m : membershipService.acceptedMembers(shop)) {
                    map.put(m.getClient().getTelegramId(), m.getClient());
                }
                for (BotUser s : userService.findSellersByShop(shop)) {
                    map.put(s.getTelegramId(), s);
                }
                recipients.addAll(map.values());
            } else if (target.startsWith("ids:")) {
                String csv = target.substring("ids:".length());
                if (!csv.isBlank()) {
                    for (String idStr : csv.split(",")) {
                        userService.findById(Long.parseLong(idStr.trim())).ifPresent(recipients::add);
                    }
                }
            }
            userService.clearState(user.getTelegramId());
            int sent = messenger.broadcast(recipients, loc.t(user, "admin.broadcast_label"), body);
            messenger.menu(user, msg.getChatId(), loc.t(user, "admin.broadcast_done", sent));
            return;
        }

        // Holat ADMIN_ bilan boshlanadi, lekin hech bir bosqichga mos kelmadi
        // (masalan ADMIN_BCAST_PICK callback orqali yakunlanadi). Qotib qolmaslik
        // uchun state'ni tozalab, foydalanuvchini asosiy menyuga qaytaramiz.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, msg.getChatId(), loc.t(user, "common.cancelled"));
    }
}
