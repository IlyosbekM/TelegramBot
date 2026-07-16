package com.qarzbot.handler.callback.common;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.handler.callback.CallbackHandler;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.ExcelExportService;
import com.qarzbot.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;

/**
 * Excel callback handler — "xls:" prefiksini ushlaydi.
 *
 * Qo'llab-quvvatlangan ma'lumotlar:
 *   xls:debts — sotuvchi o'z do'koni bo'yicha to'liq Excel hisobotni yuklab oladi
 *   xls:admin — admin barcha do'konlar bo'yicha umumiy Excel hisobotni yuklab oladi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelCallbackHandler implements CallbackHandler {

    private static final String PREFIX = "xls:";

    private final ExcelExportService excelExportService;
    private final ShopService shopService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String data) {
        return data != null && data.startsWith(PREFIX);
    }

    @Override
    public void handle(BotUser user, Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackId = update.getCallbackQuery().getId();

        if ("xls:debts".equals(data)) {
            handleShopDebts(user, chatId, callbackId);
        } else if ("xls:admin".equals(data)) {
            handleAdmin(user, chatId, callbackId);
        } else {
            messenger.answerCallback(callbackId, "");
        }
    }

    // ── xls:debts ────────────────────────────────────────────────────────────

    private void handleShopDebts(BotUser user, Long chatId, String callbackId) {
        if (user.getRole() != Role.SELLER || user.getShop() == null) {
            messenger.answerCallback(callbackId, "");
            return;
        }

        Shop shop = user.getShop();
        Lang lang = Lang.fromStored(user.getLanguage());
        messenger.answerCallback(callbackId, "");
        try {
            byte[] bytes = excelExportService.shopWorkbook(shop, lang);
            String filename = "qarzlar_" + sanitize(shop.getName()) + "_" + LocalDate.now() + ".xlsx";
            String caption = loc.t(user, "excel.caption_shop", shop.getName());
            messenger.sendDocument(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("Excel hisobot yaratishda xato (shop={}): {}", shop.getId(), e.getMessage(), e);
            messenger.send(chatId, loc.t(user, "excel.err"));
        }
    }

    // ── xls:admin ────────────────────────────────────────────────────────────

    private void handleAdmin(BotUser user, Long chatId, String callbackId) {
        if (user.getRole() != Role.ADMIN) {
            messenger.answerCallback(callbackId, "");
            return;
        }

        Lang lang = Lang.fromStored(user.getLanguage());
        messenger.answerCallback(callbackId, "");
        try {
            byte[] bytes = excelExportService.adminWorkbook(shopService.findAll(), lang);
            String filename = "hisobot_admin_" + LocalDate.now() + ".xlsx";
            String caption = loc.t(user, "excel.caption_admin");
            messenger.sendDocument(chatId, bytes, filename, caption);
        } catch (Exception e) {
            log.error("Admin Excel hisobot yaratishda xato: {}", e.getMessage(), e);
            messenger.send(chatId, loc.t(user, "excel.err"));
        }
    }

    // ── Yordamchi ────────────────────────────────────────────────────────────

    private String sanitize(String input) {
        if (input == null) {
            return "shop";
        }
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
