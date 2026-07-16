package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentPromiseRepository;
import com.qarzbot.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromiseService {

    private final PaymentPromiseRepository paymentPromiseRepository;
    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Mijoz muayyan qarz uchun to'lov va'dasi beradi.
     *
     * @param debtId          qarz ID
     * @param clientTelegramId va'da beruvchi mijozning Telegram ID'si (egalik tekshiruvi uchun)
     * @param promiseDate     mijoz to'lashga va'da bergan sana (bugundan 60 kun ichida)
     * @return yaratilgan, OPEN holatidagi PaymentPromise
     * @throws IllegalArgumentException validatsiya xatosi (o'zbek tilida, foydalanuvchiga ko'rsatish uchun)
     */
    @Transactional
    public PaymentPromise create(Long debtId, Long clientTelegramId, LocalDate promiseDate) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));

        if (!debt.getClient().getTelegramId().equals(clientTelegramId)) {
            throw new IllegalArgumentException("Bu qarz sizga tegishli emas");
        }

        if (debt.getStatus() != Debt.DebtStatus.ACTIVE && debt.getStatus() != Debt.DebtStatus.OVERDUE) {
            throw new IllegalArgumentException("Bu qarz uchun va'da berib bo'lmaydi");
        }

        if (paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN)) {
            throw new IllegalArgumentException("Bu qarz uchun ochiq va'da allaqachon bor");
        }

        LocalDate today = LocalDate.now();
        if (promiseDate.isBefore(today) || promiseDate.isAfter(today.plusDays(60))) {
            throw new IllegalArgumentException("Sana bugundan boshlab 60 kun ichida bo'lishi kerak");
        }

        PaymentPromise promise = PaymentPromise.builder()
                .debt(debt)
                .client(debt.getClient())
                .promiseDate(promiseDate)
                .amount(debt.getRemainingAmount())
                .status(PaymentPromise.PromiseStatus.OPEN)
                .build();

        return paymentPromiseRepository.save(promise);
    }

    /** Do'kon bo'yicha barcha ochiq (OPEN) va'dalar, eng yaqin sanadan boshlab. */
    public List<PaymentPromise> openForShop(Shop shop) {
        return paymentPromiseRepository.findOpenByShopFetched(shop, PaymentPromise.PromiseStatus.OPEN);
    }

    /** Aynan berilgan sanada bajarilishi kerak bo'lgan ochiq va'dalar (kunlik eslatma uchun). */
    public List<PaymentPromise> openDueOn(LocalDate date) {
        return paymentPromiseRepository.findByStatusAndPromiseDateFetched(PaymentPromise.PromiseStatus.OPEN, date);
    }

    /**
     * Muddati o'tgan (promiseDate &lt; today) ochiq va'dalarni KEPT yoki BROKEN
     * deb belgilaydi:
     * <ul>
     *   <li>KEPT — agar qarz to'liq to'langan bo'lsa (qoldiq == 0), YOKI
     *       va'da yaratilgandan keyin, va'da sanasi tugagunga qadar (promiseDate + 1 kun,
     *       kun boshigacha) kamida bitta to'lov bo'lgan bo'lsa;</li>
     *   <li>aks holda BROKEN.</li>
     * </ul>
     *
     * @param today baholash sanasi (odatda {@link LocalDate#now()})
     * @return baholangan (endi KEPT yoki BROKEN bo'lgan) va'dalar ro'yxati
     */
    @Transactional
    public List<PaymentPromise> evaluateOverdue(LocalDate today) {
        List<PaymentPromise> due = paymentPromiseRepository.findByStatusAndPromiseDateBeforeFetched(
                PaymentPromise.PromiseStatus.OPEN, today);

        for (PaymentPromise promise : due) {
            Debt debt = promise.getDebt();
            boolean kept = debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0;

            if (!kept) {
                LocalDateTime windowEnd = promise.getPromiseDate().plusDays(1).atStartOfDay();
                for (Payment payment : paymentRepository.findByDebt(debt)) {
                    if (!payment.getPaidAt().isBefore(promise.getCreatedAt())
                            && payment.getPaidAt().isBefore(windowEnd)) {
                        kept = true;
                        break;
                    }
                }
            }

            promise.setStatus(kept ? PaymentPromise.PromiseStatus.KEPT : PaymentPromise.PromiseStatus.BROKEN);
            promise.setDecidedAt(LocalDateTime.now());
            paymentPromiseRepository.save(promise);
        }

        return due;
    }
}
