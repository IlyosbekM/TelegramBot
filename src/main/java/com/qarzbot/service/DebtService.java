package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Debt createDebt(Shop shop, BotUser client, BotUser seller,
                           BigDecimal amount, String description, LocalDate dueDate) {
        Debt debt = Debt.builder()
                .shop(shop)
                .client(client)
                .seller(seller)
                .totalAmount(amount)
                .description(description)
                .dueDate(dueDate)
                .status(Debt.DebtStatus.ACTIVE)
                .build();
        return debtRepository.save(debt);
    }

    @Transactional
    public Payment addPayment(Long debtId, BigDecimal amount, String note, Long recordedBy) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Summa musbat bo'lishi kerak");
        }
        if (amount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("To'lov summasi qoldiq qarzdan oshib ketdi");
        }

        Payment payment = Payment.builder()
                .debt(debt)
                .amount(amount)
                .note(note)
                .recordedBy(recordedBy)
                .build();
        paymentRepository.save(payment);

        debt.setPaidAmount(debt.getPaidAmount().add(amount));
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(Debt.DebtStatus.PAID);
        }
        debtRepository.save(debt);
        return payment;
    }

    public Optional<Debt> findById(Long id) {
        return debtRepository.findByIdFetched(id);
    }

    public Optional<Debt> findByIdFetched(Long id) {
        return debtRepository.findByIdFetched(id);
    }

    public List<Debt> findActiveByClient(BotUser client) {
        return debtRepository.findByClientAndStatus(client, Debt.DebtStatus.ACTIVE);
    }

    public List<Debt> findAllByClient(BotUser client) {
        return debtRepository.findByClient(client);
    }

    public List<Debt> findByShop(Shop shop) {
        return debtRepository.findByShop(shop);
    }

    public List<Debt> findActiveByShop(Shop shop) {
        return debtRepository.findByShopAndStatus(shop, Debt.DebtStatus.ACTIVE);
    }

    public BigDecimal totalActiveDebt(Shop shop) {
        return debtRepository.totalActiveDebtByShop(shop);
    }

    public BigDecimal totalClientDebt(BotUser client) {
        return debtRepository.totalActiveDebtByClient(client);
    }

    public List<Debt> findDueOrOverdue(LocalDate date) {
        return debtRepository.findOverdueOrDueSoon(date);
    }

    @Transactional
    public void cancel(Long debtId) {
        debtRepository.findByIdFetched(debtId).ifPresent(d -> {
            d.setStatus(Debt.DebtStatus.CANCELLED);
            debtRepository.save(d);
        });
    }

    @Transactional
    public Debt increaseDebt(Long debtId, BigDecimal amount, String note, Long recordedBy) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        if (debt.getStatus() == Debt.DebtStatus.CANCELLED) {
            throw new IllegalArgumentException("Bekor qilingan qarzni oshirib bo'lmaydi");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Summa musbat bo'lishi kerak");
        }
        debt.setTotalAmount(debt.getTotalAmount().add(amount));
        if (note != null && !note.isBlank()) {
            String old = debt.getDescription();
            debt.setDescription((old == null || old.isBlank() ? "" : old + "; ") + note);
        }
        debt.setStatus(Debt.DebtStatus.ACTIVE);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt updateAmount(Long debtId, BigDecimal newTotal) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        if (newTotal.compareTo(debt.getPaidAmount()) < 0) {
            throw new IllegalArgumentException("Yangi summa to'langan summadan (" + debt.getPaidAmount() + ") kam bo'lmasin");
        }
        debt.setTotalAmount(newTotal);
        debt.setStatus(debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0
                ? Debt.DebtStatus.PAID : Debt.DebtStatus.ACTIVE);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt updateDescription(Long debtId, String description) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        debt.setDescription(description);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt updateDueDate(Long debtId, java.time.LocalDate dueDate) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        debt.setDueDate(dueDate);
        return debtRepository.save(debt);
    }

    @Transactional
    public void delete(Long debtId) {
        debtRepository.deleteById(debtId);
    }

    /**
     * Muddati o'tgan (dueDate &lt; today) ACTIVE qarzlarni OVERDUE holatiga o'tkazadi.
     * @return holati yangilangan qarzlar soni.
     */
    @Transactional
    public int markOverdue(LocalDate today) {
        List<Debt> overdue = debtRepository.findActiveOverdue(today);
        for (Debt d : overdue) {
            d.setStatus(Debt.DebtStatus.OVERDUE);
            debtRepository.save(d);
        }
        return overdue.size();
    }
}
