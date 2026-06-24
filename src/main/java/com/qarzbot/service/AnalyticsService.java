package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Debt.DebtStatus;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.service.dto.AnalyticsSnapshot;
import com.qarzbot.service.dto.AnalyticsSnapshot.AgingBucket;
import com.qarzbot.service.dto.AnalyticsSnapshot.TopDebtor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only analytics service — no DB writes.
 * All per-shop queries delegate to repositories; cross-shop aggregation
 * happens in Java.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final DebtRepository    debtRepository;
    private final PaymentRepository paymentRepository;

    // ── Aging bucket day-ranges ──────────────────────────────────────────────
    private static final long[] BUCKET_MIN  = {  1,  8, 31,  91 };
    private static final long[] BUCKET_MAX  = {  7, 30, 90, Long.MAX_VALUE };
    private static final String[] BUCKET_LABELS = { "1-7", "8-30", "31-90", "90+" };

    // ────────────────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────────────────

    /** Build a snapshot for a single shop (seller analytics). */
    public AnalyticsSnapshot forShop(Shop shop) {
        List<Debt> all = debtRepository.findByShop(shop);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal collected  = paymentRepository.sumCollectedSince(shop, startOfMonth);
        long paymentsThisMonth = paymentRepository.countCollectedSince(shop, startOfMonth);
        long newDebtsThisMonth = debtRepository.countNewDebtsSince(shop, startOfMonth);
        BigDecimal newDebtsAmount = debtRepository.sumNewDebtsSince(shop, startOfMonth);
        return buildSnapshot(all, collected, paymentsThisMonth, newDebtsThisMonth, newDebtsAmount);
    }

    /** Build an aggregate snapshot across multiple shops (admin analytics). */
    public AnalyticsSnapshot forShops(List<Shop> shops) {
        // Collect all debts across shops and aggregate repo scalars in Java.
        List<Debt> all = new ArrayList<>();
        BigDecimal collected       = BigDecimal.ZERO;
        long paymentsThisMonth     = 0L;
        long newDebtsThisMonth     = 0L;
        BigDecimal newDebtsAmount  = BigDecimal.ZERO;

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        for (Shop shop : shops) {
            all.addAll(debtRepository.findByShop(shop));
            collected      = collected.add(paymentRepository.sumCollectedSince(shop, startOfMonth));
            paymentsThisMonth += paymentRepository.countCollectedSince(shop, startOfMonth);
            newDebtsThisMonth += debtRepository.countNewDebtsSince(shop, startOfMonth);
            newDebtsAmount = newDebtsAmount.add(debtRepository.sumNewDebtsSince(shop, startOfMonth));
        }

        return buildSnapshot(all, collected, paymentsThisMonth, newDebtsThisMonth, newDebtsAmount);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────────────────

    private AnalyticsSnapshot buildSnapshot(
            List<Debt> all,
            BigDecimal collected,
            long paymentsThisMonth,
            long newDebtsThisMonth,
            BigDecimal newDebtsAmount) {

        LocalDate today = LocalDate.now();

        // ── Status counts & totals ──────────────────────────────────────────
        long activeCount    = 0L;
        long overdueCount   = 0L;
        long paidCount      = 0L;
        long cancelledCount = 0L;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal sumTotalAmount   = BigDecimal.ZERO;
        BigDecimal sumPaidAmount    = BigDecimal.ZERO;

        for (Debt d : all) {
            switch (d.getStatus()) {
                case ACTIVE -> {
                    activeCount++;
                    totalOutstanding = totalOutstanding.add(d.getRemainingAmount());
                    if (d.getDueDate() != null && d.getDueDate().isBefore(today)) {
                        overdueCount++;
                    }
                }
                case OVERDUE     -> overdueCount++;
                case PAID        -> paidCount++;
                case CANCELLED   -> cancelledCount++;
            }
            sumTotalAmount = sumTotalAmount.add(d.getTotalAmount());
            sumPaidAmount  = sumPaidAmount.add(d.getPaidAmount());
        }

        // ── Collection rate ─────────────────────────────────────────────────
        int collectionRatePct = 0;
        if (sumTotalAmount.signum() > 0) {
            collectionRatePct = sumPaidAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(sumTotalAmount, 0, RoundingMode.DOWN)
                    .intValue();
        }

        // ── Aging buckets (only ACTIVE debts with a dueDate in the past) ───
        List<AgingBucket> agingBuckets = computeAging(all, today);

        // ── Top debtors (ACTIVE, by remaining, top 5) ──────────────────────
        List<TopDebtor> topDebtors = computeTopDebtors(all, 5);

        return AnalyticsSnapshot.builder()
                .activeCount(activeCount)
                .overdueCount(overdueCount)
                .paidCount(paidCount)
                .cancelledCount(cancelledCount)
                .totalOutstanding(totalOutstanding)
                .collectedThisMonth(collected)
                .paymentsThisMonth(paymentsThisMonth)
                .newDebtsThisMonth(newDebtsThisMonth)
                .newDebtsAmountThisMonth(newDebtsAmount)
                .collectionRatePct(collectionRatePct)
                .agingBuckets(agingBuckets)
                .topDebtors(topDebtors)
                .build();
    }

    /**
     * Partition ACTIVE overdue debts (dueDate != null && dueDate < today) into
     * four aging buckets by days overdue.
     */
    private List<AgingBucket> computeAging(List<Debt> all, LocalDate today) {
        long[]       counts = new long[BUCKET_LABELS.length];
        BigDecimal[] sums   = new BigDecimal[BUCKET_LABELS.length];
        for (int i = 0; i < sums.length; i++) sums[i] = BigDecimal.ZERO;

        for (Debt d : all) {
            if (d.getStatus() != DebtStatus.ACTIVE) continue;
            if (d.getDueDate() == null) continue;
            if (!d.getDueDate().isBefore(today)) continue;

            long daysOverdue = ChronoUnit.DAYS.between(d.getDueDate(), today);
            for (int i = 0; i < BUCKET_LABELS.length; i++) {
                if (daysOverdue >= BUCKET_MIN[i] && daysOverdue <= BUCKET_MAX[i]) {
                    counts[i]++;
                    sums[i] = sums[i].add(d.getRemainingAmount());
                    break;
                }
            }
        }

        List<AgingBucket> buckets = new ArrayList<>(BUCKET_LABELS.length);
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            buckets.add(AgingBucket.builder()
                    .label(BUCKET_LABELS[i])
                    .count(counts[i])
                    .sum(sums[i])
                    .build());
        }
        return buckets;
    }

    /**
     * Group ACTIVE debts by client fullName, sum remaining, return top-N by remaining desc.
     */
    private List<TopDebtor> computeTopDebtors(List<Debt> all, int n) {
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();
        for (Debt d : all) {
            if (d.getStatus() != DebtStatus.ACTIVE) continue;
            String name = d.getClient().getFullName();
            grouped.merge(name, d.getRemainingAmount(), BigDecimal::add);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(n)
                .map(e -> TopDebtor.builder()
                        .name(e.getKey())
                        .remaining(e.getValue())
                        .build())
                .toList();
    }
}
