package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentPromiseRepository;
import com.qarzbot.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link PromiseService} — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PromiseServiceTest {

    @Mock
    private PaymentPromiseRepository paymentPromiseRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PromiseService promiseService;

    private Shop shop;
    private BotUser client;
    private BotUser seller;
    private Debt debt;

    @BeforeEach
    void setUp() {
        shop = Shop.builder().id(1L).name("Test do'kon").currency("UZS").build();
        client = BotUser.builder().telegramId(100L).fullName("Ali Aliyev").role(Role.CLIENT).build();
        seller = BotUser.builder().telegramId(300L).fullName("Sotuvchi Vali").role(Role.SELLER).shop(shop).build();
        debt = Debt.builder()
                .id(10L)
                .shop(shop)
                .client(client)
                .seller(seller)
                .totalAmount(new BigDecimal("100000.00"))
                .paidAmount(BigDecimal.ZERO)
                .status(Debt.DebtStatus.ACTIVE)
                .build();
    }

    // ── create() ─────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_savesOpenPromiseWithRemainingAmount() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN)).thenReturn(false);
        when(paymentPromiseRepository.save(any(PaymentPromise.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate promiseDate = LocalDate.now().plusDays(5);
        PaymentPromise result = promiseService.create(10L, 100L, promiseDate);

        assertThat(result.getStatus()).isEqualTo(PaymentPromise.PromiseStatus.OPEN);
        assertThat(result.getDebt()).isEqualTo(debt);
        assertThat(result.getClient()).isEqualTo(client);
        assertThat(result.getPromiseDate()).isEqualTo(promiseDate);
        assertThat(result.getAmount()).isEqualByComparingTo(debt.getRemainingAmount());
        verify(paymentPromiseRepository).save(any(PaymentPromise.class));
    }

    @Test
    void create_wrongClient_throwsIllegalArgumentException() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));

        assertThatThrownBy(() -> promiseService.create(10L, 999L, LocalDate.now().plusDays(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bu qarz sizga tegishli emas");

        verify(paymentPromiseRepository, never()).save(any());
    }

    @Test
    void create_existingOpenPromise_throwsIllegalArgumentException() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> promiseService.create(10L, 100L, LocalDate.now().plusDays(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bu qarz uchun ochiq va'da allaqachon bor");

        verify(paymentPromiseRepository, never()).save(any());
    }

    @Test
    void create_dateInPast_throwsIllegalArgumentException() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN)).thenReturn(false);

        assertThatThrownBy(() -> promiseService.create(10L, 100L, LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sana bugundan boshlab 60 kun ichida bo'lishi kerak");

        verify(paymentPromiseRepository, never()).save(any());
    }

    @Test
    void create_dateBeyond60Days_throwsIllegalArgumentException() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentPromiseRepository.existsByDebtAndStatus(debt, PaymentPromise.PromiseStatus.OPEN)).thenReturn(false);

        assertThatThrownBy(() -> promiseService.create(10L, 100L, LocalDate.now().plusDays(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sana bugundan boshlab 60 kun ichida bo'lishi kerak");

        verify(paymentPromiseRepository, never()).save(any());
    }

    // ── evaluateOverdue() ────────────────────────────────────────────────────

    @Test
    void evaluateOverdue_marksKept_whenQualifyingPaymentExists() {
        LocalDate today = LocalDate.of(2026, 7, 20);
        LocalDate promiseDate = today.minusDays(2);
        LocalDateTime createdAt = promiseDate.minusDays(3).atStartOfDay();

        Debt partiallyPaidDebt = Debt.builder()
                .id(20L).shop(shop).client(client).seller(seller)
                .totalAmount(new BigDecimal("100000.00"))
                .paidAmount(new BigDecimal("40000.00")) // remaining = 60000, not fully paid
                .status(Debt.DebtStatus.ACTIVE)
                .build();

        PaymentPromise promise = PaymentPromise.builder()
                .id(1L).debt(partiallyPaidDebt).client(client)
                .promiseDate(promiseDate)
                .amount(new BigDecimal("60000.00"))
                .status(PaymentPromise.PromiseStatus.OPEN)
                .createdAt(createdAt)
                .build();

        // Paid the same day as the promise date -> within [createdAt, promiseDate+1day)
        Payment qualifyingPayment = Payment.builder()
                .id(5L).debt(partiallyPaidDebt)
                .amount(new BigDecimal("40000.00"))
                .paidAt(promiseDate.atStartOfDay().plusHours(2))
                .recordedBy(300L)
                .build();

        when(paymentPromiseRepository.findByStatusAndPromiseDateBeforeFetched(PaymentPromise.PromiseStatus.OPEN, today))
                .thenReturn(List.of(promise));
        when(paymentRepository.findByDebt(partiallyPaidDebt)).thenReturn(List.of(qualifyingPayment));
        when(paymentPromiseRepository.save(any(PaymentPromise.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentPromise> result = promiseService.evaluateOverdue(today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentPromise.PromiseStatus.KEPT);
        assertThat(result.get(0).getDecidedAt()).isNotNull();
    }

    @Test
    void evaluateOverdue_marksBroken_whenNoQualifyingPaymentAndNotFullyPaid() {
        LocalDate today = LocalDate.of(2026, 7, 20);
        LocalDate promiseDate = today.minusDays(2);
        LocalDateTime createdAt = promiseDate.minusDays(3).atStartOfDay();

        Debt unpaidDebt = Debt.builder()
                .id(21L).shop(shop).client(client).seller(seller)
                .totalAmount(new BigDecimal("100000.00"))
                .paidAmount(BigDecimal.ZERO)
                .status(Debt.DebtStatus.ACTIVE)
                .build();

        PaymentPromise promise = PaymentPromise.builder()
                .id(2L).debt(unpaidDebt).client(client)
                .promiseDate(promiseDate)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentPromise.PromiseStatus.OPEN)
                .createdAt(createdAt)
                .build();

        when(paymentPromiseRepository.findByStatusAndPromiseDateBeforeFetched(PaymentPromise.PromiseStatus.OPEN, today))
                .thenReturn(List.of(promise));
        when(paymentRepository.findByDebt(unpaidDebt)).thenReturn(List.of());
        when(paymentPromiseRepository.save(any(PaymentPromise.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentPromise> result = promiseService.evaluateOverdue(today);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentPromise.PromiseStatus.BROKEN);
        assertThat(result.get(0).getDecidedAt()).isNotNull();
    }
}
