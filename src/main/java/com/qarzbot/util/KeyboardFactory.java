package com.qarzbot.util;

import com.qarzbot.entity.Role;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TelegramBots 10.0.0 da Keyboard klasslar immutable bo'lgan,
 * shuning uchun barcha klaviaturalar builder pattern orqali yaratiladi.
 */
public class KeyboardFactory {

    public static ReplyKeyboardMarkup mainMenu(Role role) {
        return mainMenu(role, Lang.UZ);
    }

    /** Til'ga bog'liq asosiy menyu: bir xil tugma joylashuvi, har bir yorliq Loc'dan tarjima qilinadi. */
    public static ReplyKeyboardMarkup mainMenu(Role role, Lang lang) {
        List<KeyboardRow> rows = new ArrayList<>();
        switch (role) {
            case ADMIN -> {
                rows.add(row(Loc.tr(lang, "menu.admin.add_shop"), Loc.tr(lang, "menu.admin.shops")));
                rows.add(row(Loc.tr(lang, "menu.admin.users"), Loc.tr(lang, "menu.admin.report")));
                rows.add(row(Loc.tr(lang, "menu.admin.settings"), Loc.tr(lang, "menu.help")));
                rows.add(row(Loc.tr(lang, "menu.admin.broadcast"), Loc.tr(lang, "menu.admin.analytics")));
            }
            case SELLER -> {
                rows.add(row(Loc.tr(lang, "menu.seller.add_debt"), Loc.tr(lang, "menu.seller.debts")));
                rows.add(row(Loc.tr(lang, "menu.seller.clients"), Loc.tr(lang, "menu.seller.search")));
                rows.add(row(Loc.tr(lang, "menu.seller.requests"), Loc.tr(lang, "menu.seller.report")));
                rows.add(row(Loc.tr(lang, "menu.seller.statistics"), Loc.tr(lang, "menu.seller.history")));
                rows.add(row(Loc.tr(lang, "menu.seller.send_reminder"), Loc.tr(lang, "menu.help")));
                rows.add(row(Loc.tr(lang, "menu.seller.products"), Loc.tr(lang, "menu.seller.broadcast")));
                rows.add(row(Loc.tr(lang, "menu.seller.analytics")));
            }
            case CLIENT -> {
                rows.add(row(Loc.tr(lang, "menu.client.my_debts"), Loc.tr(lang, "menu.client.payment_history")));
                rows.add(row(Loc.tr(lang, "menu.client.shops"), Loc.tr(lang, "menu.client.shop_search")));
                rows.add(row(Loc.tr(lang, "menu.client.open_shop"), Loc.tr(lang, "menu.client.settings")));
                rows.add(row(Loc.tr(lang, "menu.help")));
            }
        }
        return ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
    }

    public static ReplyKeyboardMarkup contactRequest() {
        return contactRequest(Lang.UZ);
    }

    public static ReplyKeyboardMarkup contactRequest(Lang lang) {
        KeyboardButton btn = KeyboardButton.builder()
                .text(Loc.tr(lang, "btn.phone"))
                .requestContact(true)
                .build();
        KeyboardRow row = new KeyboardRow();
        row.add(btn);
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(row)
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
    }

    public static ReplyKeyboardMarkup cancel() {
        return cancel(Lang.UZ);
    }

    public static ReplyKeyboardMarkup cancel(Lang lang) {
        return ReplyKeyboardMarkup.builder()
                .keyboardRow(row(Loc.tr(lang, "btn.cancel")))
                .resizeKeyboard(true)
                .build();
    }

    /** Til tanlash inline klaviaturasi (yorliqlar tildan mustaqil). */
    public static InlineKeyboardMarkup languageMenu() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(inline("🇺🇿 O'zbekcha", "lang:uz")));
        rows.add(new InlineKeyboardRow(inline("🇷🇺 Русский", "lang:ru")));
        rows.add(new InlineKeyboardRow(inline("🇬🇧 English", "lang:en")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup debtActions(Long debtId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("💵 To'lov", "pay:" + debtId),
                inline("➕ Oshirish", "increase:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("✏️ Tahrirlash", "edit:" + debtId),
                inline("🗑 O'chirish", "delete:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("📜 Tarix", "history:" + debtId),
                inline("⏰ Eslatma", "remind:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("🧾 PDF", "pdf:debt:" + debtId),
                inline("📷 QR", "qr:debt:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("📅 Bo'lib to'lash", "inst:start:" + debtId),
                inline("📋 Jadval", "inst:view:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("↩️ To'lovni qaytarish", "payundo:ask:" + debtId)
        ));
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup editMenu(Long debtId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("💰 Summa", "editamount:" + debtId),
                inline("📝 Izoh", "editdesc:" + debtId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("📅 Muddat", "editdue:" + debtId)
        ));
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup deleteConfirm(Long debtId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("✅ Ha, o'chirish", "delyes:" + debtId),
                inline("❌ Yo'q", "delno:" + debtId)
        ));
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup clientActions(Long clientTelegramId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("📋 Qarzlari", "clientdebts:" + clientTelegramId),
                inline("📊 To'liq hisobot", "statement:" + clientTelegramId)
        ));
        rows.add(new InlineKeyboardRow(
                inline("⭐ Ishonch ball", "trust:" + clientTelegramId),
                inline("📄 PDF hisobot", "pdf:statement:" + clientTelegramId)
        ));
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Klient sozlamalari: avtomatik eslatmalarni yoqish/o'chirish toggle tugmasi.
     * callbackData = "remtoggle" (ClientSettingsCallbackHandler tomonidan ushlanadi).
     */
    public static InlineKeyboardMarkup clientSettings(boolean remindersEnabled) {
        return clientSettings(remindersEnabled, Lang.UZ);
    }

    public static InlineKeyboardMarkup clientSettings(boolean remindersEnabled, Lang lang) {
        String label = remindersEnabled
                ? Loc.tr(lang, "settings.reminders_on")
                : Loc.tr(lang, "settings.reminders_off");
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(inline(label, "remtoggle"))))
                .build();
    }

    // ── Mahsulot katalogi (Product) klaviaturalari ───────────────────────────

    /** Sotuvchi mahsulotlar ro'yxati; har biri "prodview:ID", oxirida "➕ Yangi mahsulot" (prodadd). */
    public static InlineKeyboardMarkup sellerProductList(List<com.qarzbot.entity.Product> products) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (com.qarzbot.entity.Product p : products) {
            String label = "🛒 " + p.getName() + " — " + MessageFormatter.money(p.getPrice())
                    + (p.getUnit() != null && !p.getUnit().isBlank() ? "/" + p.getUnit() : "");
            rows.add(new InlineKeyboardRow(inline(label, "prodview:" + p.getId())));
        }
        rows.add(new InlineKeyboardRow(inline("➕ Yangi mahsulot", "prodadd")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Bitta mahsulot amallari: tahrirlash / o'chirish. */
    public static InlineKeyboardMarkup productActions(Long productId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("✏️ Tahrirlash", "prodedit:" + productId),
                inline("🗑 O'chirish", "proddel:" + productId)
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup productDeleteConfirm(Long productId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("✅ Ha, o'chirish", "proddelyes:" + productId),
                inline("❌ Yo'q", "proddelno")
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Qarz qo'shish usuli: mahsulotdan tanlash yoki qo'lda summa. */
    public static InlineKeyboardMarkup addDebtMode(Long clientId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(inline("🛒 Mahsulot tanlash", "dprod:" + clientId)));
        rows.add(new InlineKeyboardRow(inline("✍️ Qo'lda summa", "dmanual:" + clientId)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Qarz qo'shishda mahsulot tanlash ro'yxati; callback "dpick:clientId:productId". */
    public static InlineKeyboardMarkup debtProductList(List<com.qarzbot.entity.Product> products, Long clientId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (com.qarzbot.entity.Product p : products) {
            String label = p.getName() + " — " + MessageFormatter.money(p.getPrice())
                    + (p.getUnit() != null && !p.getUnit().isBlank() ? "/" + p.getUnit() : "");
            rows.add(new InlineKeyboardRow(inline(label, "dpick:" + clientId + ":" + p.getId())));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup singleInline(String text, String data) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(inline(text, data))))
                .build();
    }

    /**
     * Qabul qilish / Rad etish inline tugmalari.
     * Membership va PaymentRequest tasdiqlari uchun ishlatiladi.
     */
    public static InlineKeyboardMarkup acceptReject(String prefixAcc, String prefixRej, Long id) {
        InlineKeyboardRow row = new InlineKeyboardRow(
                inline("✅ Qabul qilish", prefixAcc + id),
                inline("❌ Bekor qilish", prefixRej + id)
        );
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    /** Sotuvchi "📥 So'rovlar" ekrani ostidagi qo'shimcha bo'limlar (va'dalar, e'tirozlar). */
    public static InlineKeyboardMarkup sellerRequestExtras() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("🤝 Va'dalar", "vada:list"),
                inline("⚠️ E'tirozlar", "etiroz:list")
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Admin "⚙️ Sozlamalar" ekrani amallari: Excel hisobot + to'liq zaxira nusxa. */
    public static InlineKeyboardMarkup adminSettingsActions() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("📥 Excel hisobot", "xls:admin"),
                inline("💾 Zaxira nusxa", "backup:run")
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // Admin: choose broadcast target audience
    public static InlineKeyboardMarkup broadcastTargets() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(inline("👥 Hammaga", "bcast:all")));
        rows.add(new InlineKeyboardRow(inline("🏪 Do'kon bo'yicha", "bcast:byshop")));
        rows.add(new InlineKeyboardRow(inline("✅ Tanlab yuborish", "bcast:select")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // Admin: list shops to broadcast to one shop's users
    public static InlineKeyboardMarkup broadcastShopList(List<com.qarzbot.entity.Shop> shops) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (com.qarzbot.entity.Shop s : shops) {
            rows.add(new InlineKeyboardRow(inline("🏪 " + s.getName(), "bcastshop:" + s.getId())));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // Admin: multi-select user list; selected ids get a ✅ prefix. Final row = done button.
    public static InlineKeyboardMarkup broadcastUserSelect(List<com.qarzbot.entity.BotUser> users,
                                                           java.util.Set<Long> selected) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (com.qarzbot.entity.BotUser u : users) {
            String mark = selected.contains(u.getTelegramId()) ? "✅ " : "▫️ ";
            rows.add(new InlineKeyboardRow(inline(mark + u.getFullName(), "bpick:" + u.getTelegramId())));
        }
        rows.add(new InlineKeyboardRow(inline("📨 Davom etish", "bpickdone")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup shopList(List<com.qarzbot.entity.Shop> shops, Long userId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (com.qarzbot.entity.Shop s : shops) {
            rows.add(new InlineKeyboardRow(
                    inline("🏪 " + s.getName(), "setseller:" + userId + ":" + s.getId())
            ));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Sotuvchi analitika ekrani uchun: reyting (leaderboard) + tezkor filtrlar (inline). */
    public static InlineKeyboardMarkup analyticsActions() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(
                inline("🏆 Ishonchli mijozlar", "lead:reliable"),
                inline("💸 Yirik qarzdorlar", "lead:debtors")
        ));
        rows.add(new InlineKeyboardRow(
                inline("📋 Faol", "filter:active"),
                inline("⚠️ Muddati o'tgan", "filter:overdue"),
                inline("💰 Yirik", "filter:big")
        ));
        rows.add(new InlineKeyboardRow(inline("🔍 Mijoz qidirish", "filter:search")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private static KeyboardRow row(String... buttons) {
        KeyboardRow r = new KeyboardRow();
        r.addAll(Arrays.asList(buttons));
        return r;
    }

    private static InlineKeyboardButton inline(String text, String data) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(data)
                .build();
    }
}
