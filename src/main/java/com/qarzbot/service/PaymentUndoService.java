package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Sotuvchi tomonidan noto'g'ri kiritilgan to'lovni bekor qilish ("Payment Undo").
 * Faqat qarzdagi ENG OXIRGI to'lov, va faqat u yozilgandan keyingi 24 soat ichida bekor qilinishi mumkin.
 * Bekor qilinganda: Payment o'chiriladi, debt.paidAmount qaytariladi, status qayta hisoblanadi.
 *
 * ⚠️ Debt.payments (OneToMany, cascade=ALL + orphanRemoval=true) hech qachon shu servisda
 * initsializatsiya/mutatsiya qilinmaydi — to'lov PaymentRepository orqali to'g'ridan-to'g'ri o'chiriladi.
 */
@Service
@RequiredArgsConstructor
public class PaymentUndoService {

    private static final Duration UNDO_WINDOW = Duration.ofHours(24);

    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Berilgan qarz uchun bekor qilinishi mumkin bo'lgan ENG OXIRGI to'lovni topadi va barcha
     * qoidalarni tekshiradi. Hech narsani o'zgartirmaydi (faqat validatsiya/o'qish).
     *
     * @param debtId qarz identifikatori
     * @param seller amalni bajarayotgan sotuvchi (BotUser, role=SELLER bo'lishi kutiladi — rol
     *               tekshiruvi chaqiruvchi callback handler'da amalga oshiriladi)
     * @return bekor qilinishi mumkin bo'lgan oxirgi Payment
     * @throws IllegalArgumentException validatsiya xatoligi bo'lsa (sabab xabari o'zbek tilida)
     */
    public Payment lastUndoable(Long debtId, BotUser seller) {
        if (seller.getShop() == null) {
            throw new IllegalArgumentException("Sizga do'kon biriktirilmagan");
        }
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        ensureOwnership(debt, seller);
        return latestWithinWindowOrThrow(debt);
    }

    /**
     * Berilgan to'lovni bekor qiladi: shart-sharoitlarni qayta tekshiradi (shu jumladan bu
     * to'lov hali ham ENG OXIRGI ekanligini), debt.paidAmount'ni qaytaradi, statusni qayta
     * hisoblaydi (CANCELLED holatini pasaytirmaydi) va Payment yozuvini o'chiradi.
     *
     * @param paymentId bekor qilinadigan to'lov identifikatori
     * @param seller amalni bajarayotgan sotuvchi
     * @return o'chirilgan Payment (chaqiruvchi tomonda hali ham asosiy maydonlari — id, amount,
     *         note, paidAt, recordedBy — o'qish uchun xavfsiz; lekin uning debt/shop/client kabi
     *         LAZY bog'lanishlari ushbu tranzaksiyadan tashqarida qayta ishlatilmasin — chaqiruvchi
     *         kerakli ma'lumotlarni debtRepository.findByIdFetched orqali qayta yuklab olsin)
     * @throws IllegalArgumentException validatsiya xatoligi bo'lsa
     */
    @Transactional
    public Payment undo(Long paymentId, BotUser seller) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("To'lov topilmadi"));
        Debt debt = payment.getDebt();

        if (seller.getShop() == null) {
            throw new IllegalArgumentException("Sizga do'kon biriktirilmagan");
        }
        ensureOwnership(debt, seller);
        Payment latest = latestWithinWindowOrThrow(debt);
        if (!latest.getId().equals(payment.getId())) {
            throw new IllegalArgumentException("Bu to'lov oxirgi emas — faqat oxirgi to'lovni bekor qilish mumkin");
        }

        BigDecimal newPaid = debt.getPaidAmount().subtract(payment.getAmount());
        if (newPaid.signum() < 0) {
            throw new IllegalArgumentException("Ichki xatolik: to'lov summasi mos emas");
        }
        debt.setPaidAmount(newPaid);

        if (debt.getStatus() != Debt.DebtStatus.CANCELLED) {
            BigDecimal remaining = debt.getTotalAmount().subtract(newPaid);
            if (remaining.signum() > 0) {
                boolean overdue = debt.getDueDate() != null && debt.getDueDate().isBefore(LocalDate.now());
                debt.setStatus(overdue ? Debt.DebtStatus.OVERDUE : Debt.DebtStatus.ACTIVE);
            } else {
                debt.setStatus(Debt.DebtStatus.PAID);
            }
        }
        debtRepository.save(debt);
        paymentRepository.delete(payment);
        return payment;
    }

    // ── Yordamchi metodlar ──────────────────────────────────────────────────

    private void ensureOwnership(Debt debt, BotUser seller) {
        if (!debt.getShop().getId().equals(seller.getShop().getId())) {
            throw new IllegalArgumentException("Bu qarz sizning do'koningizga tegishli emas");
        }
    }

    /** Qarzdagi to'lovlar orasidan eng oxirgisini (paidAt, keyin id bo'yicha) topadi va 24-soatlik oynani tekshiradi. */
    private Payment latestWithinWindowOrThrow(Debt debt) {
        List<Payment> payments = paymentRepository.findByDebt(debt);
        Payment latest = payments.stream()
                .max(Comparator.comparing(Payment::getPaidAt).thenComparing(Payment::getId))
                .orElse(null);
        if (latest == null) {
            throw new IllegalArgumentException("Bu qarzda to'lovlar yo'q");
        }
        if (Duration.between(latest.getPaidAt(), LocalDateTime.now()).compareTo(UNDO_WINDOW) > 0) {
            throw new IllegalArgumentException("Faqat oxirgi 24 soat ichidagi to'lovni bekor qilish mumkin");
        }
        return latest;
    }
}
