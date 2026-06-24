package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Installment;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.InstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstallmentService {

    private final InstallmentRepository installmentRepository;
    private final DebtRepository debtRepository;

    /**
     * Qarz qoldiqini {@code count} teng bo'lakka bo'ladi.
     * Oxirgi bo'lak yaxlitlash qoldiqini o'z ichiga oladi (sum == remaining).
     *
     * @param debtId     qarz ID
     * @param count      bo'laklar soni (2–24)
     * @param periodDays bo'laklar orasidagi kun soni (>=1)
     * @return tartiblangan Installment ro'yxati
     * @throws IllegalArgumentException validation xatosi
     */
    @Transactional
    public List<Installment> createSchedule(Long debtId, int count, int periodDays) {
        if (count < 2 || count > 24) {
            throw new IllegalArgumentException("inst.err_count");
        }
        if (periodDays < 1) {
            throw new IllegalArgumentException("inst.err_period");
        }

        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("inst.err_debt_not_found"));

        // Mavjud bo'laklarni o'chir (re-planning)
        installmentRepository.deleteByDebt(debt);
        installmentRepository.flush();

        BigDecimal remaining = debt.getRemainingAmount();
        // Har bir bo'lak = remaining / count (2 xonagacha)
        BigDecimal each = remaining.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);

        List<Installment> installments = new ArrayList<>(count);
        BigDecimal sumSoFar = BigDecimal.ZERO;

        for (int i = 0; i < count; i++) {
            boolean isLast = (i == count - 1);
            BigDecimal amount = isLast ? remaining.subtract(sumSoFar) : each;
            LocalDate dueDate = LocalDate.now().plusDays((long) periodDays * (i + 1));

            Installment inst = Installment.builder()
                    .debt(debt)
                    .seqNo(i + 1)
                    .dueDate(dueDate)
                    .amount(amount)
                    .paid(false)
                    .build();
            installments.add(inst);
            sumSoFar = sumSoFar.add(amount);
        }

        return installmentRepository.saveAll(installments);
    }

    /**
     * Qarz bo'yicha bo'laklar jadvalini qaytaradi (seqNo bo'yicha tartibda).
     * Debt ham FETCH qilinadi.
     */
    public List<Installment> getSchedule(Long debtId) {
        return installmentRepository.findByDebtIdFetched(debtId);
    }

    /**
     * Bo'lakni to'langan deb belgilaydi.
     * Debt ham FETCH qilinadi (LazyInitializationException oldini olish uchun).
     *
     * @param installmentId bo'lak ID
     * @return yangilangan Installment (debt bilan birga)
     */
    @Transactional
    public Installment markPaid(Long installmentId) {
        Installment inst = installmentRepository.findByIdFetched(installmentId)
                .orElseThrow(() -> new IllegalArgumentException("inst.err_not_found"));
        inst.setPaid(true);
        inst.setPaidDate(LocalDate.now());
        return installmentRepository.save(inst);
    }
}
