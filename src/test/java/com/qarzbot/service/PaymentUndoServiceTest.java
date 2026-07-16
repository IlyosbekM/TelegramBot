package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JUnit 5 + Mockito unit tests for {@link PaymentUndoService}. No Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PaymentUndoServiceTest {

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentUndoService paymentUndoService;

    private Shop shop;
    private BotUser seller;
    private BotUser client;
    private Debt debt;

    @BeforeEach
    void setUp() {
        shop = Shop.builder()
                .id(1L)
                .name("Test do'kon")
                .currency("UZS")
                .ownerTelegramId(100L)
                .createdAt(LocalDateTime.now())
                .build();

        seller = BotUser.builder()
                .telegramId(200L)
                .fullName("Sotuvchi")
                .role(Role.SELLER)
                .shop(shop)
                .createdAt(LocalDateTime.now())
                .build();

        client = BotUser.builder()
                .telegramId(300L)
                .fullName("Mijoz")
                .role(Role.CLIENT)
                .createdAt(LocalDateTime.now())
                .build();

        debt = Debt.builder()
                .id(10L)
                .shop(shop)
                .client(client)
                .seller(seller)
                .totalAmount(new BigDecimal("100000"))
                .paidAmount(new BigDecimal("40000"))
                .status(Debt.DebtStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Payment payment(Long id, BigDecimal amount, LocalDateTime paidAt) {
        return Payment.builder()
                .id(id)
                .debt(debt)
                .amount(amount)
                .paidAt(paidAt)
                .recordedBy(seller.getTelegramId())
                .build();
    }

    // ── lastUndoable ─────────────────────────────────────────────────────────

    @Test
    void lastUndoable_picksNewestOfSeveralPayments() {
        Payment p1 = payment(1L, new BigDecimal("10000"), LocalDateTime.now().minusHours(5));
        Payment p2 = payment(2L, new BigDecimal("20000"), LocalDateTime.now().minusHours(1)); // newest
        Payment p3 = payment(3L, new BigDecimal("10000"), LocalDateTime.now().minusHours(3));

        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(p1, p2, p3));

        Payment result = paymentUndoService.lastUndoable(10L, seller);

        assertEquals(2L, result.getId());
    }

    @Test
    void lastUndoable_emptyPayments_throws() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.lastUndoable(10L, seller));
        assertEquals("Bu qarzda to'lovlar yo'q", ex.getMessage());
    }

    @Test
    void lastUndoable_olderThan24Hours_throws() {
        Payment old = payment(1L, new BigDecimal("10000"), LocalDateTime.now().minusHours(25));

        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(old));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.lastUndoable(10L, seller));
        assertEquals("Faqat oxirgi 24 soat ichidagi to'lovni bekor qilish mumkin", ex.getMessage());
    }

    @Test
    void lastUndoable_wrongShop_throws() {
        Shop otherShop = Shop.builder()
                .id(999L)
                .name("Boshqa do'kon")
                .currency("UZS")
                .ownerTelegramId(500L)
                .createdAt(LocalDateTime.now())
                .build();
        BotUser otherSeller = BotUser.builder()
                .telegramId(201L)
                .fullName("Boshqa sotuvchi")
                .role(Role.SELLER)
                .shop(otherShop)
                .createdAt(LocalDateTime.now())
                .build();

        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.lastUndoable(10L, otherSeller));
        assertEquals("Bu qarz sizning do'koningizga tegishli emas", ex.getMessage());
    }

    @Test
    void lastUndoable_noShopAssigned_throws() {
        BotUser shoplessSeller = BotUser.builder()
                .telegramId(202L)
                .fullName("Do'konsiz sotuvchi")
                .role(Role.SELLER)
                .createdAt(LocalDateTime.now())
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.lastUndoable(10L, shoplessSeller));
        assertEquals("Sizga do'kon biriktirilmagan", ex.getMessage());
    }

    // ── undo ─────────────────────────────────────────────────────────────────

    @Test
    void undo_revertsPaidAmount_andFlipsPaidToActive_whenNoDueDate() {
        debt.setTotalAmount(new BigDecimal("40000"));
        debt.setPaidAmount(new BigDecimal("40000"));
        debt.setStatus(Debt.DebtStatus.PAID);
        debt.setDueDate(null);

        Payment payment = payment(5L, new BigDecimal("40000"), LocalDateTime.now().minusMinutes(30));

        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(payment));

        Payment result = paymentUndoService.undo(5L, seller);

        assertEquals(0, debt.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(Debt.DebtStatus.ACTIVE, debt.getStatus());
        assertEquals(payment, result);
        verify(debtRepository).save(debt);
        verify(paymentRepository).delete(payment);
    }

    @Test
    void undo_flipsToActive_whenDueDateInFuture() {
        debt.setTotalAmount(new BigDecimal("40000"));
        debt.setPaidAmount(new BigDecimal("40000"));
        debt.setStatus(Debt.DebtStatus.PAID);
        debt.setDueDate(LocalDate.now().plusDays(5));

        Payment payment = payment(5L, new BigDecimal("15000"), LocalDateTime.now().minusMinutes(10));

        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(payment));

        paymentUndoService.undo(5L, seller);

        assertEquals(0, debt.getPaidAmount().compareTo(new BigDecimal("25000")));
        assertEquals(Debt.DebtStatus.ACTIVE, debt.getStatus());
        verify(paymentRepository).delete(payment);
    }

    @Test
    void undo_flipsToOverdue_whenDueDateInPast() {
        debt.setTotalAmount(new BigDecimal("40000"));
        debt.setPaidAmount(new BigDecimal("40000"));
        debt.setStatus(Debt.DebtStatus.PAID);
        debt.setDueDate(LocalDate.now().minusDays(2));

        Payment payment = payment(5L, new BigDecimal("15000"), LocalDateTime.now().minusMinutes(10));

        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(payment));

        paymentUndoService.undo(5L, seller);

        assertEquals(0, debt.getPaidAmount().compareTo(new BigDecimal("25000")));
        assertEquals(Debt.DebtStatus.OVERDUE, debt.getStatus());
        verify(paymentRepository).delete(payment);
    }

    @Test
    void undo_doesNotDowngradeCancelledDebt() {
        debt.setTotalAmount(new BigDecimal("40000"));
        debt.setPaidAmount(new BigDecimal("40000"));
        debt.setStatus(Debt.DebtStatus.CANCELLED);
        debt.setDueDate(LocalDate.now().minusDays(2)); // would be OVERDUE if not CANCELLED

        Payment payment = payment(5L, new BigDecimal("15000"), LocalDateTime.now().minusMinutes(10));

        when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(payment));

        paymentUndoService.undo(5L, seller);

        assertEquals(Debt.DebtStatus.CANCELLED, debt.getStatus());
        verify(paymentRepository).delete(payment);
    }

    @Test
    void undo_refusesWhenPaymentIsNotTheLatest() {
        Payment older = payment(1L, new BigDecimal("10000"), LocalDateTime.now().minusHours(5));
        Payment newest = payment(2L, new BigDecimal("20000"), LocalDateTime.now().minusHours(1));

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(older));
        when(paymentRepository.findByDebt(debt)).thenReturn(List.of(older, newest));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.undo(1L, seller));
        assertEquals("Bu to'lov oxirgi emas — faqat oxirgi to'lovni bekor qilish mumkin", ex.getMessage());

        verify(paymentRepository, never()).delete(any());
        verify(debtRepository, never()).save(any());
    }

    @Test
    void undo_paymentNotFound_throws() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> paymentUndoService.undo(999L, seller));
        assertEquals("To'lov topilmadi", ex.getMessage());
    }
}
