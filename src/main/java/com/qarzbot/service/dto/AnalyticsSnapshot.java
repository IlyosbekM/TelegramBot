package com.qarzbot.service.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computed analytics metrics for a shop (or aggregate of shops).
 * Immutable value object built by AnalyticsService.
 */
@Getter
@Builder
public class AnalyticsSnapshot {

    // ── KPI counts ──────────────────────────────────────────────────────────
    private final long activeCount;
    private final long overdueCount;
    private final long paidCount;
    private final long cancelledCount;

    // ── Outstanding (ACTIVE debts remaining) ────────────────────────────────
    private final BigDecimal totalOutstanding;

    // ── This-month activity ─────────────────────────────────────────────────
    private final BigDecimal collectedThisMonth;
    private final long      paymentsThisMonth;
    private final long      newDebtsThisMonth;
    private final BigDecimal newDebtsAmountThisMonth;

    /** paid / total across ALL debts × 100, guarded against divide-by-zero */
    private final int collectionRatePct;

    // ── Aging buckets (ACTIVE & overdue) ────────────────────────────────────
    private final List<AgingBucket> agingBuckets;

    // ── Top debtors ─────────────────────────────────────────────────────────
    private final List<TopDebtor> topDebtors;

    // ── Nested value types ───────────────────────────────────────────────────

    /**
     * One aging bucket: label describes the range, count/sum are for ACTIVE overdue debts.
     */
    @Getter
    @Builder
    public static class AgingBucket {
        private final String     label;
        private final long       count;
        private final BigDecimal sum;
    }

    /**
     * A client's name and their total remaining balance across ACTIVE debts.
     */
    @Getter
    @Builder
    public static class TopDebtor {
        private final String     name;
        private final BigDecimal remaining;
    }
}
