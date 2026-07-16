package com.qarzbot.i18n;

import jakarta.annotation.PostConstruct;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lokalizatsiya qilingan reply-keyboard tugma matnlarini kanonik (UZ) matnga
 * teskari moslaydi. Bu bilan handler/router'lar avvalgidek UZ tugma satrlari
 * bo'yicha switch-case qiladi — RU/EN tugmalar bosilsa ham UZ holatga tushadi.
 *
 * canonical(label): noma'lum (erkin matn) — o'zgartirilmasdan qaytadi.
 */
@Component
public class Menus {

    private final MessageSource ms;

    /** Barcha menu.* kalitlar — har bir til uchun label -> UZ (kanonik) label. */
    private static final String[] MENU_KEYS = {
            // admin
            "menu.admin.add_shop", "menu.admin.shops", "menu.admin.users",
            "menu.admin.report", "menu.admin.settings", "menu.help", "menu.admin.broadcast",
            "menu.admin.analytics",
            // seller
            "menu.seller.add_debt", "menu.seller.debts", "menu.seller.clients",
            "menu.seller.search", "menu.seller.requests", "menu.seller.report",
            "menu.seller.statistics", "menu.seller.history", "menu.seller.send_reminder",
            "menu.seller.products", "menu.seller.broadcast", "menu.seller.analytics",
            // client
            "menu.client.my_debts", "menu.client.payment_history", "menu.client.shops",
            "menu.client.shop_search", "menu.client.open_shop", "menu.client.settings",
            // buttons that are routed as menu text
            "btn.cancel"
    };

    /** localizedLabel -> UZ (kanonik) label */
    private final Map<String, String> canonicalMap = new HashMap<>();
    /** btn.cancel barcha tillardagi qiymatlari */
    private final Set<String> cancelLabels = new HashSet<>();
    /** menu.help barcha tillardagi qiymatlari */
    private final Set<String> helpLabels = new HashSet<>();

    private String uzCancel;
    private String uzHelp;

    public Menus(MessageSource ms) {
        this.ms = ms;
    }

    @PostConstruct
    void build() {
        for (String key : MENU_KEYS) {
            String uzLabel = resolve(key, Lang.UZ);
            for (Lang lang : Lang.values()) {
                String label = resolve(key, lang);
                if (label != null) {
                    canonicalMap.put(label, uzLabel);
                }
            }
            if ("btn.cancel".equals(key)) {
                uzCancel = uzLabel;
                for (Lang lang : Lang.values()) cancelLabels.add(resolve(key, lang));
            }
            if ("menu.help".equals(key)) {
                uzHelp = uzLabel;
                for (Lang lang : Lang.values()) helpLabels.add(resolve(key, lang));
            }
        }
    }

    private String resolve(String key, Lang lang) {
        try {
            return ms.getMessage(key, null, lang.toLocale());
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Lokalizatsiya qilingan tugma matnini kanonik (UZ) matnga moslaydi.
     * Noma'lum tugma yoki erkin matn — o'zgartirilmasdan qaytadi.
     */
    public String canonical(String label) {
        if (label == null) return null;
        return canonicalMap.getOrDefault(label, label);
    }

    /** "Bekor qilish" tugmasimi (har qanday tilda)? */
    public boolean isCancel(String label) {
        if (label == null) return false;
        if (cancelLabels.contains(label)) return true;
        return uzCancel != null && uzCancel.equals(canonical(label));
    }

    /** "Yordam" tugmasimi (har qanday tilda)? */
    public boolean isHelp(String label) {
        if (label == null) return false;
        if (helpLabels.contains(label)) return true;
        return uzHelp != null && uzHelp.equals(canonical(label));
    }
}
