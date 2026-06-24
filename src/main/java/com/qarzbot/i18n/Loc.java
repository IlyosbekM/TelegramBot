package com.qarzbot.i18n;

import com.qarzbot.entity.BotUser;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Lokalizatsiya yordamchisi — barcha foydalanuvchiga ko'rinadigan matnlar shu orqali
 * tarjima kalitlaridan (messages_*.properties) olinadi.
 *
 * Spring MessageSource'ni o'rab oladi va static {@link #tr(Lang, String, Object...)} yordamida
 * (KeyboardFactory, MessageFormatter kabi static utilita'lar uchun) ham kalitlarni hal qiladi.
 *
 * Hech qachon istisno tashlamaydi: MessageSource mavjud bo'lmasa yoki kalit topilmasa,
 * kalitning o'zini qaytaradi.
 */
@Component
public class Loc {

    private final MessageSource ms;
    private static MessageSource MS;

    public Loc(MessageSource ms) {
        this.ms = ms;
        Loc.MS = ms;
    }

    /** Foydalanuvchining saqlangan tili bo'yicha tarjima qiladi. */
    public String t(BotUser u, String key, Object... args) {
        Lang lang = Lang.fromStored(u == null ? null : u.getLanguage());
        return tr(lang, key, args);
    }

    /** Berilgan til bo'yicha tarjima qiladi. */
    public String t(Lang lang, String key, Object... args) {
        return tr(lang, key, args);
    }

    /**
     * Static tarjima — static utilita'lar (KeyboardFactory, MessageFormatter) uchun.
     * MS null bo'lsa yoki kalit topilmasa kalitning o'zini qaytaradi (hech qachon throw qilmaydi).
     */
    public static String tr(Lang lang, String key, Object... args) {
        if (MS == null) return key;
        try {
            return MS.getMessage(key, args, lang.toLocale());
        } catch (Exception e) {
            return key;
        }
    }
}
