package com.qarzbot.i18n;

import java.util.Locale;

/**
 * Bot qo'llab-quvvatlaydigan tillar. Har bir til ResourceBundle locale kodi bilan bog'langan.
 * UZ — standart (fallback) til.
 */
public enum Lang {
    UZ("uz"),
    RU("ru"),
    EN("en");

    private final String code;

    Lang(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public Locale toLocale() {
        return new Locale(code);
    }

    /**
     * Telegram'dan kelgan language_code (masalan "ru", "ru-RU", "en-US", "uz") asosida tilni aniqlaydi.
     * null/bo'sh -> UZ; aks holda prefiks bo'yicha; noma'lum -> UZ.
     */
    public static Lang fromCode(String tgCode) {
        if (tgCode == null || tgCode.isBlank()) return UZ;
        String c = tgCode.toLowerCase();
        if (c.startsWith("ru")) return RU;
        if (c.startsWith("en")) return EN;
        if (c.startsWith("uz")) return UZ;
        return UZ;
    }

    /**
     * DB'da saqlangan til kodi ("uz"/"ru"/"en") asosida tilni aniqlaydi.
     * null/noma'lum -> UZ.
     */
    public static Lang fromStored(String s) {
        if (s == null) return UZ;
        String c = s.trim().toLowerCase();
        return switch (c) {
            case "uz" -> UZ;
            case "ru" -> RU;
            case "en" -> EN;
            default -> UZ;
        };
    }
}
