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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates ranked leaderboard views for a shop.
 *
 * Entry fields:
 *   name  — client's full name
 *   badge — medal emoji for top-3 (🥇🥈🥉), empty string for 4th and 5th
 *   value — depends on the list type:
 *             mostReliable  → BigDecimal.valueOf(score)  (for display)
 *             topDebtors    → total remaining (ACTIVE) amount
 *   score — trust score (null for topDebtors list if you prefer; we set it for both)
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final DebtRepository debtRepository;
    private final TrustScoreService trustScoreService;

    private static final String[] BADGES = {"🥇", "🥈", "🥉", "", ""};

    // ── Entry record ─────────────────────────────────────────────────────────

    public record Entry(String name, String badge, BigDecimal value, Integer score) {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Top-5 most-reliable clients in a shop ranked by trust score (descending).
     * Uses a single DB fetch for all shop debts; computes each client's score
     * from the already-loaded subset (no N+1 queries).
     */
    @Transactional(readOnly = true)
    public List<Entry> mostReliable(Shop shop) {
        List<Debt> shopDebts = debtRepository.findByShop(shop);

        // Collect distinct clients and group their debts
        Map<Long, BotUser> clientMap = new LinkedHashMap<>();
        Map<Long, List<Debt>> debtsByClient = new HashMap<>();
        for (Debt d : shopDebts) {
            BotUser c = d.getClient();
            clientMap.putIfAbsent(c.getTelegramId(), c);
            debtsByClient.computeIfAbsent(c.getTelegramId(), k -> new ArrayList<>()).add(d);
        }

        // Compute trust score per client from the pre-grouped list (avoids N+1)
        List<Map.Entry<BotUser, TrustScore>> scored = clientMap.values().stream()
                .map(c -> Map.entry(c, trustScoreService.computeFromDebts(
                        debtsByClient.getOrDefault(c.getTelegramId(), List.of()))))
                .sorted(Comparator.comparingInt((Map.Entry<BotUser, TrustScore> e) -> e.getValue().score()).reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            BotUser client = scored.get(i).getKey();
            TrustScore ts = scored.get(i).getValue();
            String badge = i < BADGES.length ? BADGES[i] : "";
            result.add(new Entry(client.getFullName(), badge, BigDecimal.valueOf(ts.score()), ts.score()));
        }
        return result;
    }

    /**
     * Top-5 biggest debtors in a shop by total remaining (ACTIVE debts) descending.
     * Also shows trust score alongside each entry.
     */
    @Transactional(readOnly = true)
    public List<Entry> topDebtors(Shop shop) {
        // Load all shop debts once (for score computation) and active debts separately
        List<Debt> allShopDebts = debtRepository.findByShop(shop);
        List<Debt> activeDebts = debtRepository.findByShopAndStatus(shop, Debt.DebtStatus.ACTIVE);

        // Group ALL debts by client (for score)
        Map<Long, List<Debt>> allDebtsByClient = new HashMap<>();
        for (Debt d : allShopDebts) {
            allDebtsByClient.computeIfAbsent(d.getClient().getTelegramId(), k -> new ArrayList<>()).add(d);
        }

        // Sum remaining per client from ACTIVE debts
        Map<Long, BigDecimal> remainingByClient = new HashMap<>();
        Map<Long, BotUser> clientMap = new HashMap<>();

        for (Debt d : activeDebts) {
            BotUser c = d.getClient();
            clientMap.put(c.getTelegramId(), c);
            remainingByClient.merge(c.getTelegramId(), d.getRemainingAmount(), BigDecimal::add);
        }

        List<Map.Entry<Long, BigDecimal>> sorted = remainingByClient.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Long telegramId = sorted.get(i).getKey();
            BigDecimal remaining = sorted.get(i).getValue();
            BotUser client = clientMap.get(telegramId);
            String badge = i < BADGES.length ? BADGES[i] : "";
            // Compute score from pre-grouped list (no extra DB query)
            TrustScore ts = trustScoreService.computeFromDebts(
                    allDebtsByClient.getOrDefault(telegramId, List.of()));
            result.add(new Entry(client.getFullName(), badge, remaining, ts.score()));
        }
        return result;
    }
}
