package com.qarzbot.service.dto;

import java.math.BigDecimal;

/**
 * Immutable snapshot of a client's credit-reliability score for a given shop (or globally).
 *
 * score         — 0..100 composite score
 * ratingKey     — i18n key: trust.rating.excellent / .good / .fair / .poor
 * recommendedLimit — suggested max debt ceiling (never negative)
 * totalDebts    — all debts counted in the calculation
 * paidDebts     — debts with status PAID
 * overdueDebts  — debts that are overdue (OVERDUE status or ACTIVE + dueDate past)
 * onTimeRatePct — paidDebts / max(1, totalDebts) * 100, rounded down
 */
public record TrustScore(
        int score,
        String ratingKey,
        BigDecimal recommendedLimit,
        long totalDebts,
        long paidDebts,
        long overdueDebts,
        int onTimeRatePct
) {
    /** Convenience: returns the localised rating band key. */
    public static String ratingKeyForScore(int score) {
        if (score >= 85) return "trust.rating.excellent";
        if (score >= 70) return "trust.rating.good";
        if (score >= 50) return "trust.rating.fair";
        return "trust.rating.poor";
    }
}
