package com.qarzbot.handler;

import com.qarzbot.entity.Role;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import org.springframework.stereotype.Component;

/**
 * Markazlashtirilgan yordam (qo'llanma) matnlari.
 * Har bir rol uchun sodda, tushunarli Markdown matn qaytaradi.
 * Handler'lar (Client/Seller/Admin) shu service'dan foydalanadi —
 * matnlar bitta joyda saqlanadi.
 */
@Component
public class HelpService {

    public String clientHelp() {
        return clientHelp(Lang.UZ);
    }

    public String clientHelp(Lang lang) {
        return Loc.tr(lang, "help.client");
    }

    public String sellerHelp() {
        return sellerHelp(Lang.UZ);
    }

    public String sellerHelp(Lang lang) {
        return Loc.tr(lang, "help.seller");
    }

    public String adminHelp() {
        return adminHelp(Lang.UZ);
    }

    public String adminHelp(Lang lang) {
        return Loc.tr(lang, "help.admin");
    }

    /** Rolega mos yordam matni (tilga bog'liq). */
    public String help(Role role, Lang lang) {
        return switch (role) {
            case ADMIN -> adminHelp(lang);
            case SELLER -> sellerHelp(lang);
            case CLIENT -> clientHelp(lang);
        };
    }
}
