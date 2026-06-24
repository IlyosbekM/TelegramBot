package com.qarzbot.handler.seller;

import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.dto.AnalyticsSnapshot;
import com.qarzbot.service.dto.AnalyticsSnapshot.AgingBucket;
import com.qarzbot.service.dto.AnalyticsSnapshot.TopDebtor;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Converts an {@link AnalyticsSnapshot} into a Telegram-ready Markdown string.
 * All user-facing text comes from i18n keys under the {@code analytics.*} prefix.
 * Amounts are formatted via {@link MessageFormatter#money(BigDecimal, Lang)}.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsPresenter {

    /** Maximum bar width (block characters) for the ASCII aging chart. */
    private static final int BAR_MAX = 10;

    private final Loc loc;

    /**
     * Render a full analytics report as a Markdown string.
     *
     * @param s    the computed snapshot
     * @param lang the user's display language
     * @return Markdown-formatted report string
     */
    public String render(AnalyticsSnapshot s, Lang lang) {
        // Guard: empty data
        if (s.getActiveCount() == 0 && s.getPaidCount() == 0
                && s.getCancelledCount() == 0 && s.getOverdueCount() == 0) {
            return loc.t(lang, "analytics.empty");
        }

        StringBuilder sb = new StringBuilder();

        // ── Title ────────────────────────────────────────────────────────────
        sb.append(loc.t(lang, "analytics.title")).append("\n\n");

        // ── Headline KPIs ────────────────────────────────────────────────────
        sb.append(loc.t(lang, "analytics.outstanding",
                MessageFormatter.money(s.getTotalOutstanding(), lang))).append("\n");
        sb.append(loc.t(lang, "analytics.active",    s.getActiveCount())).append("\n");
        sb.append(loc.t(lang, "analytics.overdue",   s.getOverdueCount())).append("\n");
        sb.append(loc.t(lang, "analytics.paid",      s.getPaidCount())).append("\n");
        sb.append(loc.t(lang, "analytics.cancelled", s.getCancelledCount())).append("\n");

        // ── Collection rate ──────────────────────────────────────────────────
        sb.append("\n");
        sb.append(loc.t(lang, "analytics.collection_rate", s.getCollectionRatePct())).append("\n");

        // ── This-month activity ──────────────────────────────────────────────
        sb.append("\n");
        sb.append(loc.t(lang, "analytics.this_month")).append("\n");
        sb.append(loc.t(lang, "analytics.new_debts_month",
                s.getNewDebtsThisMonth(),
                MessageFormatter.money(s.getNewDebtsAmountThisMonth(), lang))).append("\n");
        sb.append(loc.t(lang, "analytics.collected_month",
                s.getPaymentsThisMonth(),
                MessageFormatter.money(s.getCollectedThisMonth(), lang))).append("\n");

        // ── Aging chart ──────────────────────────────────────────────────────
        List<AgingBucket> buckets = s.getAgingBuckets();
        if (buckets != null && !buckets.isEmpty()) {
            // Find the max count to scale bars
            long maxCount = buckets.stream()
                    .mapToLong(AgingBucket::getCount)
                    .max()
                    .orElse(0L);

            sb.append("\n");
            sb.append(loc.t(lang, "analytics.aging_header")).append("\n");
            for (AgingBucket b : buckets) {
                String bar = buildBar(b.getCount(), maxCount);
                sb.append(loc.t(lang, "analytics.aging_bucket",
                        b.getLabel(),
                        b.getCount(),
                        MessageFormatter.money(b.getSum(), lang),
                        bar)).append("\n");
            }
        }

        // ── Top debtors ──────────────────────────────────────────────────────
        List<TopDebtor> top = s.getTopDebtors();
        if (top != null && !top.isEmpty()) {
            sb.append("\n");
            sb.append(loc.t(lang, "analytics.top_debtors")).append("\n");
            int rank = 1;
            for (TopDebtor td : top) {
                sb.append(loc.t(lang, "analytics.top_debtor_line",
                        rank++,
                        td.getName(),
                        MessageFormatter.money(td.getRemaining(), lang))).append("\n");
            }
        }

        return sb.toString();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Build an ASCII bar of '█' characters proportional to {@code count / maxCount},
     * capped at {@link #BAR_MAX} blocks. Returns an empty string when maxCount == 0.
     */
    private String buildBar(long count, long maxCount) {
        if (maxCount == 0L) return "";
        int blocks = (int) Math.round((double) count / maxCount * BAR_MAX);
        if (blocks == 0 && count > 0) blocks = 1; // at least one block if non-zero
        return "█".repeat(blocks);
    }
}
