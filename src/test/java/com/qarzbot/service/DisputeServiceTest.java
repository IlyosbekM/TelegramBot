package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Dispute;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisputeService}. Pure Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;
    @Mock
    private DebtRepository debtRepository;

    @InjectMocks
    private DisputeService disputeService;

    private Shop shop;
    private BotUser client;
    private Debt debt;

    @BeforeEach
    void setUp() {
        shop = Shop.builder()
                .id(1L)
                .name("Test Do'kon")
                .ownerTelegramId(100L)
                .currency("UZS")
                .build();

        client = BotUser.builder()
                .telegramId(200L)
                .fullName("Client One")
                .role(Role.CLIENT)
                .build();

        debt = Debt.builder()
                .id(10L)
                .shop(shop)
                .client(client)
                .totalAmount(BigDecimal.valueOf(100000))
                .paidAmount(BigDecimal.ZERO)
                .status(Debt.DebtStatus.ACTIVE)
                .build();
    }

    @Test
    void open_happyPath_createsOpenDispute() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(disputeRepository.existsByDebtAndStatus(debt, Dispute.DisputeStatus.OPEN)).thenReturn(false);
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        Dispute result = disputeService.open(10L, 200L, "  Summa noto'g'ri hisoblangan  ");

        assertNotNull(result);
        assertEquals(Dispute.DisputeStatus.OPEN, result.getStatus());
        assertEquals("Summa noto'g'ri hisoblangan", result.getReason());
        assertEquals(debt, result.getDebt());
        assertEquals(client, result.getClient());
        verify(disputeRepository).save(any(Dispute.class));
    }

    @Test
    void open_wrongOwner_throws() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disputeService.open(10L, 999L, "Valid reason text"));

        assertEquals("Bu qarz sizga tegishli emas", ex.getMessage());
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void open_duplicateOpenDispute_throws() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(disputeRepository.existsByDebtAndStatus(debt, Dispute.DisputeStatus.OPEN)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disputeService.open(10L, 200L, "Valid reason text"));

        assertEquals("Bu qarz bo'yicha ochiq e'tiroz allaqachon bor", ex.getMessage());
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void open_reasonTooShort_throws() {
        when(debtRepository.findByIdFetched(10L)).thenReturn(Optional.of(debt));
        when(disputeRepository.existsByDebtAndStatus(debt, Dispute.DisputeStatus.OPEN)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disputeService.open(10L, 200L, "hi"));

        assertEquals("Sabab 3 dan 400 gacha belgidan iborat bo'lsin", ex.getMessage());
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void accept_wrongShop_throws() {
        Shop otherShop = Shop.builder().id(2L).name("Other Shop").ownerTelegramId(500L).build();
        Dispute dispute = Dispute.builder()
                .id(50L)
                .debt(debt)
                .client(client)
                .reason("Reason text")
                .status(Dispute.DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findByIdFetched(50L)).thenReturn(Optional.of(dispute));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disputeService.accept(50L, otherShop));

        assertEquals("Bu e'tiroz sizning do'koningizga tegishli emas", ex.getMessage());
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void accept_alreadyDecided_throws() {
        Dispute dispute = Dispute.builder()
                .id(51L)
                .debt(debt)
                .client(client)
                .reason("Reason text")
                .status(Dispute.DisputeStatus.ACCEPTED)
                .createdAt(LocalDateTime.now())
                .decidedAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findByIdFetched(51L)).thenReturn(Optional.of(dispute));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disputeService.accept(51L, shop));

        assertEquals("E'tiroz allaqachon ko'rib chiqilgan", ex.getMessage());
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void reject_setsNoteAndStatus() {
        Dispute dispute = Dispute.builder()
                .id(52L)
                .debt(debt)
                .client(client)
                .reason("Reason text")
                .status(Dispute.DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(disputeRepository.findByIdFetched(52L)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        Dispute result = disputeService.reject(52L, shop, "  Chek asosida tekshirildi, xato yo'q  ");

        assertEquals(Dispute.DisputeStatus.REJECTED, result.getStatus());
        assertEquals("Chek asosida tekshirildi, xato yo'q", result.getResolutionNote());
        assertNotNull(result.getDecidedAt());
        verify(disputeRepository).save(dispute);
    }
}
