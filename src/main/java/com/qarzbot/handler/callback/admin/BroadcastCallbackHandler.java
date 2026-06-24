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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BroadcastCallbackHandler implements CallbackHandler {

    private final BotMessenger messenger;
    private final UserService userService;
    private final ShopService shopService;

    @Override
    public boolean supports(String data) {
        return data.startsWith("bcast:") || data.startsWith("bcastshop:") || data.startsWith("bpick:") || data.equals("bpickdone");
    }

    @Override
    public void handle(BotUser user, Update update) {
        if (user.getRole() != Role.ADMIN) return;
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        if (data.equals("bcast:all")) {
            userService.setState(user.getTelegramId(), "ADMIN_BCAST_TEXT:all");
            messenger.send(chatId, "✍️ Hamma foydalanuvchiga yuboriladigan xabar matnini kiriting:");
        } else if (data.equals("bcast:byshop")) {
            List<Shop> shops = shopService.findAll();
            if (shops.isEmpty()) {
                messenger.send(chatId, "Hech qanday do'kon yo'q.");
            } else {
                messenger.send(chatId, "🏪 Qaysi do'kon foydalanuvchilariga?",
                        KeyboardFactory.broadcastShopList(shops));
            }
        } else if (data.startsWith("bcastshop:")) {
            Long shopId = Long.parseLong(data.substring("bcastshop:".length()));
            userService.setState(user.getTelegramId(), "ADMIN_BCAST_TEXT:shop:" + shopId);
            messenger.send(chatId, "✍️ Tanlangan do'kon foydalanuvchilariga yuboriladigan xabar matnini kiriting:");
        } else if (data.equals("bcast:select")) {
            userService.setState(user.getTelegramId(), "ADMIN_BCAST_PICK:");
            List<BotUser> users = broadcastCandidates();
            if (users.isEmpty()) {
                messenger.send(chatId, "Foydalanuvchilar yo'q.");
            } else {
                messenger.send(chatId,
                        "✅ Foydalanuvchilarni tanlang, so'ng \"📨 Davom etish\" ni bosing:",
                        KeyboardFactory.broadcastUserSelect(users, new HashSet<>()));
            }
        } else if (data.startsWith("bpick:")) {
            Long pickedId = Long.parseLong(data.substring("bpick:".length()));
            BotUser freshAdmin = userService.findById(user.getTelegramId()).orElse(user);
            String st = freshAdmin.getState() != null ? freshAdmin.getState() : "ADMIN_BCAST_PICK:";
            String csv = st.startsWith("ADMIN_BCAST_PICK:") ? st.substring("ADMIN_BCAST_PICK:".length()) : "";
            LinkedHashSet<Long> selected = new LinkedHashSet<>();
            if (!csv.isBlank()) {
                for (String idStr : csv.split(",")) {
                    try { selected.add(Long.parseLong(idStr.trim())); } catch (NumberFormatException ignored) {}
                }
            }
            if (selected.contains(pickedId)) {
                selected.remove(pickedId);
            } else {
                selected.add(pickedId);
            }
            String newCsv = selected.stream().map(String::valueOf).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
            userService.setState(user.getTelegramId(), "ADMIN_BCAST_PICK:" + newCsv);
            List<BotUser> users = broadcastCandidates();
            messenger.execute(EditMessageReplyMarkup.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .replyMarkup(KeyboardFactory.broadcastUserSelect(users, selected))
                    .build());
        } else if (data.equals("bpickdone")) {
            BotUser freshAdmin = userService.findById(user.getTelegramId()).orElse(user);
            String st = freshAdmin.getState() != null ? freshAdmin.getState() : "ADMIN_BCAST_PICK:";
            String csv = st.startsWith("ADMIN_BCAST_PICK:") ? st.substring("ADMIN_BCAST_PICK:".length()) : "";
            if (csv.isBlank()) {
                messenger.send(chatId, "❌ Hech kim tanlanmadi.");
                return;
            }
            userService.setState(user.getTelegramId(), "ADMIN_BCAST_TEXT:ids:" + csv);
            messenger.send(chatId, "✍️ Tanlangan foydalanuvchilarga yuboriladigan xabar matnini kiriting:");
        }
    }

    private List<BotUser> broadcastCandidates() {
        return userService.findAll().stream().filter(u -> u.getRole() != Role.ADMIN).toList();
    }
}
