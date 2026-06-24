package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.ProductService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class SellerProductFlow implements ConversationFlow {

    private final UserService userService;
    private final ProductService productService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("PROD_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Long chatId = msg.getChatId();
        Shop shop = user.getShop();
        Lang lang = Lang.fromStored(user.getLanguage());

        if (shop == null) {
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "err.no_shop_assigned"));
            return;
        }

        // ── ADD flow ──────────────────────────────────────────────────────────

        if (state.equals("PROD_ADD_NAME")) {
            String name = text.trim();
            if (name.isEmpty()) {
                messenger.send(chatId, loc.t(user, "prod.ask_name"), KeyboardFactory.cancel(lang));
                return;
            }
            userService.setState(user.getTelegramId(), "PROD_ADD_PRICE:" + name);
            messenger.send(chatId, loc.t(user, "prod.ask_price"), KeyboardFactory.cancel(lang));
            return;
        }

        if (state.startsWith("PROD_ADD_PRICE:")) {
            String name = state.substring("PROD_ADD_PRICE:".length());
            BigDecimal price;
            try {
                price = new BigDecimal(text.replaceAll("\\s+", ""));
                if (price.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.send(chatId, loc.t(user, "prod.err_price"), KeyboardFactory.cancel(lang));
                return;
            }
            userService.setState(user.getTelegramId(), "PROD_ADD_UNIT:" + name + ":" + price.toPlainString());
            messenger.send(chatId, loc.t(user, "prod.ask_unit"), KeyboardFactory.cancel(lang));
            return;
        }

        if (state.startsWith("PROD_ADD_UNIT:")) {
            // payload = name:price  (price is last segment, name is everything before last ':')
            String payload = state.substring("PROD_ADD_UNIT:".length());
            int lastColon = payload.lastIndexOf(':');
            String name = payload.substring(0, lastColon);
            String priceStr = payload.substring(lastColon + 1);
            String unit = "-".equals(text.trim()) ? null : text.trim();
            String unitPart = unit == null ? "-" : unit;
            userService.setState(user.getTelegramId(), "PROD_ADD_STOCK:" + name + ":" + priceStr + ":" + unitPart);
            messenger.send(chatId, loc.t(user, "prod.ask_stock"), KeyboardFactory.cancel(lang));
            return;
        }

        if (state.startsWith("PROD_ADD_STOCK:")) {
            // payload = name:price:unit  (split from the right)
            String payload = state.substring("PROD_ADD_STOCK:".length());
            int u = payload.lastIndexOf(':');
            String unitPart = payload.substring(u + 1);
            int p = payload.lastIndexOf(':', u - 1);
            String pricePart = payload.substring(p + 1, u);
            String name = payload.substring(0, p);

            String unit = "-".equals(unitPart) ? null : unitPart;
            BigDecimal price = new BigDecimal(pricePart);

            Integer stock;
            try {
                stock = "-".equals(text.trim()) ? null : Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                messenger.send(chatId, loc.t(user, "prod.err_stock"), KeyboardFactory.cancel(lang));
                return;
            }

            productService.create(shop, name, price, unit, stock);
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "prod.added", name, MessageFormatter.money(price, lang)));
            return;
        }

        // ── EDIT flow ─────────────────────────────────────────────────────────

        if (state.startsWith("PROD_EDIT_NAME:")) {
            Long id = Long.parseLong(state.substring("PROD_EDIT_NAME:".length()));
            String name = "-".equals(text.trim()) ? null : text.trim();
            String namePart = name == null ? "-" : name;
            userService.setState(user.getTelegramId(), "PROD_EDIT_PRICE:" + id + ":" + namePart);
            messenger.send(chatId, loc.t(user, "prod.ask_new_price"), KeyboardFactory.cancel(lang));
            return;
        }

        if (state.startsWith("PROD_EDIT_PRICE:")) {
            // payload = id:name  (id is first segment up to first ':')
            String payload = state.substring("PROD_EDIT_PRICE:".length());
            int firstColon = payload.indexOf(':');
            Long id = Long.parseLong(payload.substring(0, firstColon));
            String namePart = payload.substring(firstColon + 1);
            String name = "-".equals(namePart) ? null : namePart;

            BigDecimal price;
            if ("-".equals(text.trim())) {
                price = null;
            } else {
                try {
                    price = new BigDecimal(text.replaceAll("\\s+", ""));
                    if (price.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    messenger.send(chatId, loc.t(user, "prod.err_price_edit"), KeyboardFactory.cancel(lang));
                    return;
                }
            }

            productService.update(id, name, price, null, null);
            userService.clearState(user.getTelegramId());
            messenger.menu(user, chatId, loc.t(user, "prod.updated"));
            return;
        }

        // ── FALLBACK ──────────────────────────────────────────────────────────
        // State began with PROD_ but matched nothing — unblock the user.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, chatId, loc.t(user, "common.cancelled"));
    }
}
