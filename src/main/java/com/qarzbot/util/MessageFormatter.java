package com.qarzbot.util;

import com.qarzbot.entity.Debt;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public class MessageFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator(' ');
        MONEY = new DecimalFormat("#,##0", sym);
    }

    public static String money(BigDecimal amount) {
        return money(amount, Lang.UZ);
    }

    /** Pul: bo'sh joy bilan guruhlangan son + tilga mos {@code money.suffix}. */
    public static String money(BigDecimal amount, Lang lang) {
        if (amount == null) return "0";
        return MONEY.format(amount) + " " + Loc.tr(lang, "money.suffix");
    }

    /** Pul: do'kon valyutasiga mos suffix (currency.&lt;CODE&gt;), tilga mos. */
    public static String money(BigDecimal amount, String currency, Lang lang) {
        if (amount == null) return "0";
        String code = (currency == null || currency.isBlank()) ? "UZS" : currency;
        String suffix = Loc.tr(lang, "currency." + code);
        // Agar kalit topilmasa Loc kalitning o'zini qaytaradi — bunda valyuta kodiga qaytamiz.
        if (suffix.equals("currency." + code)) {
            suffix = code;
        }
        return MONEY.format(amount) + " " + suffix;
    }

    public static String formatDebt(Debt d) {
        return formatDebt(d, Lang.UZ);
    }

    public static String formatDebt(Debt d, Lang lang) {
        String currency = d.getShop() != null ? d.getShop().getCurrency() : null;
        StringBuilder sb = new StringBuilder();
        sb.append(Loc.tr(lang, "debt.title", d.getId())).append("\n");
        sb.append(Loc.tr(lang, "debt.client", d.getClient().getFullName())).append("\n");
        sb.append(Loc.tr(lang, "debt.shop", d.getShop().getName())).append("\n");
        sb.append(Loc.tr(lang, "debt.total", money(d.getTotalAmount(), currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "debt.paid", money(d.getPaidAmount(), currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "debt.remaining", money(d.getRemainingAmount(), currency, lang))).append("\n");
        if (d.getDescription() != null && !d.getDescription().isBlank()) {
            sb.append(Loc.tr(lang, "debt.desc", d.getDescription())).append("\n");
        }
        if (d.getDueDate() != null) {
            sb.append(Loc.tr(lang, "debt.due", d.getDueDate().format(DATE_FMT))).append("\n");
            if (d.getStatus() == Debt.DebtStatus.ACTIVE) {
                LocalDate today = LocalDate.now();
                long kun = ChronoUnit.DAYS.between(today, d.getDueDate());
                if (kun < 0) {
                    sb.append(Loc.tr(lang, "debt.overdue_days", -kun)).append("\n");
                } else if (kun == 0) {
                    sb.append(Loc.tr(lang, "debt.due_today")).append("\n");
                } else {
                    sb.append(Loc.tr(lang, "debt.days_left", kun)).append("\n");
                }
            }
        }
        int pct = d.getTotalAmount().signum() == 0 ? 0
                : d.getPaidAmount().multiply(BigDecimal.valueOf(100))
                    .divide(d.getTotalAmount(), 0, RoundingMode.DOWN).intValue();
        sb.append(Loc.tr(lang, "debt.paid_pct", pct)).append("\n");
        sb.append(Loc.tr(lang, "debt.status", statusLabel(d.getStatus(), lang))).append("\n");
        return sb.toString();
    }

    public static String formatDebtList(List<Debt> debts) {
        return formatDebtList(debts, Lang.UZ);
    }

    public static String formatDebtList(List<Debt> debts, Lang lang) {
        if (debts.isEmpty()) return Loc.tr(lang, "debt.list_empty");
        StringBuilder sb = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;
        for (Debt d : debts) {
            String currency = d.getShop() != null ? d.getShop().getCurrency() : null;
            sb.append(Loc.tr(lang, "debt.list_item",
                            d.getId(),
                            d.getClient().getFullName(),
                            money(d.getRemainingAmount(), currency, lang)))
              .append("\n");
            total = total.add(d.getRemainingAmount());
        }
        sb.append("\n").append(Loc.tr(lang, "debt.list_total", money(total, lang)));
        return sb.toString();
    }

    private static String statusEmoji(Debt.DebtStatus s) {
        return statusLabel(s, Lang.UZ);
    }

    public static String statusLabel(Debt.DebtStatus s, Lang lang) {
        return switch (s) {
            case ACTIVE -> Loc.tr(lang, "status.active");
            case PAID -> Loc.tr(lang, "status.paid");
            case OVERDUE -> Loc.tr(lang, "status.overdue");
            case CANCELLED -> Loc.tr(lang, "status.cancelled");
        };
    }

    public static String formatDebtReceipt(Debt d) {
        return formatDebtReceipt(d, Lang.UZ);
    }

    public static String formatDebtReceipt(Debt d, Lang lang) {
        String currency = d.getShop() != null ? d.getShop().getCurrency() : null;
        StringBuilder sb = new StringBuilder();
        sb.append(Loc.tr(lang, "receipt.new_title")).append("\n");
        sb.append(Loc.tr(lang, "receipt.divider")).append("\n");
        sb.append(Loc.tr(lang, "receipt.shop", d.getShop().getName())).append("\n");
        sb.append(Loc.tr(lang, "receipt.client", d.getClient().getFullName())).append("\n");
        sb.append(Loc.tr(lang, "receipt.date", LocalDateTime.now().format(DATETIME_FMT))).append("\n");
        sb.append(Loc.tr(lang, "receipt.debt_id", d.getId())).append("\n");
        if (d.getDescription() != null && !d.getDescription().isBlank()) {
            sb.append(Loc.tr(lang, "receipt.desc", d.getDescription())).append("\n");
        }
        sb.append(Loc.tr(lang, "receipt.amount", money(d.getTotalAmount(), currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "receipt.remaining", money(d.getRemainingAmount(), currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "receipt.divider")).append("\n");
        sb.append(Loc.tr(lang, "receipt.thanks"));
        return sb.toString();
    }

    public static String formatPaymentReceipt(Debt d, BigDecimal paidAmount) {
        return formatPaymentReceipt(d, paidAmount, Lang.UZ);
    }

    public static String formatPaymentReceipt(Debt d, BigDecimal paidAmount, Lang lang) {
        String currency = d.getShop() != null ? d.getShop().getCurrency() : null;
        StringBuilder sb = new StringBuilder();
        sb.append(Loc.tr(lang, "receipt.payment_title")).append("\n");
        sb.append(Loc.tr(lang, "receipt.divider")).append("\n");
        sb.append(Loc.tr(lang, "receipt.shop", d.getShop().getName())).append("\n");
        sb.append(Loc.tr(lang, "receipt.client", d.getClient().getFullName())).append("\n");
        sb.append(Loc.tr(lang, "receipt.date", LocalDateTime.now().format(DATETIME_FMT))).append("\n");
        sb.append(Loc.tr(lang, "receipt.debt_id", d.getId())).append("\n");
        sb.append(Loc.tr(lang, "receipt.paid", money(paidAmount, currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "receipt.new_remaining", money(d.getRemainingAmount(), currency, lang))).append("\n");
        sb.append(Loc.tr(lang, "receipt.divider")).append("\n");
        sb.append(Loc.tr(lang, "receipt.thanks"));
        return sb.toString();
    }
}
