package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Advanced debt search and filtering service for sellers.
 * All filtering is performed in Java after a single DB fetch per shop.
 */
@Service
@RequiredArgsConstructor
public class DebtSearchService {

    private final DebtRepository debtRepository;

    /**
     * Filter criteria for debt search.
     *
     * @param status       filter by exact status (nullable — any status)
     * @param overdueOnly  if true, only ACTIVE debts whose dueDate < today
     * @param minRemaining minimum remaining amount inclusive (nullable)
     * @param maxRemaining maximum remaining amount inclusive (nullable)
     * @param nameQuery    case-insensitive substring match on client's fullName (nullable)
     */
    public record Filter(
            Debt.DebtStatus status,
            boolean overdueOnly,
            BigDecimal minRemaining,
            BigDecimal maxRemaining,
            String nameQuery
    ) {}

    /**
     * Fetch all debts for the shop and apply every non-null criterion in Java.
     */
    public List<Debt> filter(Shop shop, Filter f) {
        List<Debt> all = debtRepository.findByShop(shop);
        LocalDate today = LocalDate.now();

        return all.stream()
                .filter(d -> {
                    // status filter
                    if (f.status() != null && d.getStatus() != f.status()) return false;
                    // overdue filter: ACTIVE and dueDate < today
                    if (f.overdueOnly()) {
                        if (d.getStatus() != Debt.DebtStatus.ACTIVE) return false;
                        if (d.getDueDate() == null) return false;
                        if (!d.getDueDate().isBefore(today)) return false;
                    }
                    // minRemaining filter
                    if (f.minRemaining() != null
                            && d.getRemainingAmount().compareTo(f.minRemaining()) < 0) return false;
                    // maxRemaining filter
                    if (f.maxRemaining() != null
                            && d.getRemainingAmount().compareTo(f.maxRemaining()) > 0) return false;
                    // name query filter (case-insensitive substring)
                    if (f.nameQuery() != null && !f.nameQuery().isBlank()) {
                        String name = d.getClient().getFullName();
                        if (name == null) return false;
                        if (!name.toLowerCase().contains(f.nameQuery().toLowerCase())) return false;
                    }
                    return true;
                })
                .toList();
    }

    /** All ACTIVE debts for the shop. */
    public List<Debt> quickActive(Shop shop) {
        return filter(shop, new Filter(Debt.DebtStatus.ACTIVE, false, null, null, null));
    }

    /** ACTIVE debts whose dueDate is before today (overdue). */
    public List<Debt> quickOverdue(Shop shop) {
        return filter(shop, new Filter(null, true, null, null, null));
    }

    /**
     * ACTIVE debts sorted descending by remaining amount.
     * Returns all ACTIVE debts ordered biggest-first so the seller sees the largest debts at the top.
     */
    public List<Debt> quickBig(Shop shop) {
        return filter(shop, new Filter(Debt.DebtStatus.ACTIVE, false, null, null, null))
                .stream()
                .sorted(Comparator.comparing(Debt::getRemainingAmount).reversed())
                .toList();
    }
}
