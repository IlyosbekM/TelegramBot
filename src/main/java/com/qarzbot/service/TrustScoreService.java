package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.service.dto.TrustScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Computes a deterministic (no randomness) trust / credit-reliability score for a client.
 *
 * Scoring algorithm (pure Java, reads from already-loaded debt list — no extra queries):
 *
 *   Base = 60
 *   For each PAID debt:
 *     +5 base bonus
 *     +3 extra if paid on time (last payment paidAt.toLocalDate() <= dueDate, when both present)
 *   For each OVERDUE debt (status==OVERDUE OR status==ACTIVE with dueDate < today):
 *     -8 penalty
 *   Clamp to [0, 100].
 *
 * recommendedLimit:
 *   avgPaid = average totalAmount of PAID debts  (0 if none)
 *   multiplier = score / 50.0   (so score-100 → 2x, score-50 → 1x, score-0 → 0x)
 *   limit = avgPaid * multiplier, floored to 0.
 */
@Service
@RequiredArgsConstructor
public class TrustScoreService {

    private final DebtRepository debtRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Score for a client within a specific shop.
     */
    @Transactional(readOnly = true)
    public TrustScore forClientInShop(BotUser client, Shop shop) {
        List<Debt> all = debtRepository.findByShop(shop);
        List<Debt> clientDebts = all.stream()
                .filter(d -> d.getClient().getTelegramId().equals(client.getTelegramId()))
                .toList();
        return compute(clientDebts);
    }

    /**
     * Global score for a client across all shops.
     */
    @Transactional(readOnly = true)
    public TrustScore forClientGlobal(BotUser client) {
        List<Debt> clientDebts = debtRepository.findByClient(client);
        return compute(clientDebts);
    }

    // ── Package-visible: compute from a pre-loaded list (avoids extra DB query) ─

    /**
     * Compute trust score from a caller-supplied list of debts for this client.
     * Package-visible so LeaderboardService (same package hierarchy) can avoid N+1.
     */
    TrustScore computeFromDebts(List<Debt> clientDebts) {
        return compute(clientDebts);
    }

    // ── Core scoring logic ────────────────────────────────────────────────────

    private TrustScore compute(List<Debt> debts) {
        LocalDate today = LocalDate.now();

        long totalDebts = debts.size();
        long paidDebts = 0;
        long overdueDebts = 0;

        int score = 60;
        BigDecimal paidSum = BigDecimal.ZERO;

        for (Debt d : debts) {
            boolean isOverdue = d.getStatus() == Debt.DebtStatus.OVERDUE
                    || (d.getStatus() == Debt.DebtStatus.ACTIVE
                        && d.getDueDate() != null
                        && d.getDueDate().isBefore(today));

            if (d.getStatus() == Debt.DebtStatus.PAID) {
                paidDebts++;
                score += 5; // base bonus per paid debt

                // Extra bonus if paid on time: check last payment's paidAt vs dueDate
                if (d.getDueDate() != null && !d.getPayments().isEmpty()) {
                    LocalDate lastPaymentDate = d.getPayments().stream()
                            .map(p -> p.getPaidAt().toLocalDate())
                            .max(LocalDate::compareTo)
                            .orElse(null);
                    if (lastPaymentDate != null && !lastPaymentDate.isAfter(d.getDueDate())) {
                        score += 3; // on-time bonus
                    }
                }

                paidSum = paidSum.add(d.getTotalAmount());

            } else if (isOverdue) {
                overdueDebts++;
                score -= 8; // penalty per overdue debt
            }
        }

        // Clamp
        score = Math.max(0, Math.min(100, score));

        // onTimeRatePct
        int onTimeRatePct = (int) (paidDebts * 100L / Math.max(1, totalDebts));

        // recommendedLimit
        BigDecimal avgPaid = paidDebts == 0
                ? BigDecimal.ZERO
                : paidSum.divide(BigDecimal.valueOf(paidDebts), 2, RoundingMode.DOWN);
        BigDecimal multiplier = BigDecimal.valueOf(score).divide(BigDecimal.valueOf(50), 4, RoundingMode.DOWN);
        BigDecimal recommendedLimit = avgPaid.multiply(multiplier).max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);

        String ratingKey = TrustScore.ratingKeyForScore(score);

        return new TrustScore(score, ratingKey, recommendedLimit, totalDebts, paidDebts, overdueDebts, onTimeRatePct);
    }
}
