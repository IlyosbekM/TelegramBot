package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * JUnit 5 + Mockito (Spring context yo'q). {@link Loc} MessageSource'siz ("new Loc(null)")
 * qurilgan — Loc.tr(...) statik MS null bo'lganda kalitning o'zini qaytaradi
 * (ishlab chiqarish kodidagi xuddi shu fallback xatti-harakati), shu sababli
 * haqiqiy messages_*.properties fayllarisiz ham hujayralar bo'sh qolmaydi.
 */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock
    private DebtService debtService;
    @Mock
    private MembershipService membershipService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private DebtRepository debtRepository;

    private ExcelExportService service;

    private Shop shop;
    private BotUser client;
    private Debt activeDebt;
    private Debt paidDebt;

    @BeforeEach
    void setUp() {
        Loc loc = new Loc(null);
        service = new ExcelExportService(debtService, membershipService, paymentRepository, debtRepository, loc);

        shop = Shop.builder()
                .id(1L)
                .name("Test Do'kon")
                .address("Toshkent")
                .ownerTelegramId(100L)
                .currency("UZS")
                .createdAt(LocalDateTime.now())
                .build();

        client = BotUser.builder()
                .telegramId(200L)
                .fullName("Aliyev Vali")
                .phoneNumber("+998901234567")
                .role(Role.CLIENT)
                .createdAt(LocalDateTime.now())
                .build();

        BotUser seller = BotUser.builder()
                .telegramId(300L)
                .fullName("Sotuvchi")
                .role(Role.SELLER)
                .shop(shop)
                .createdAt(LocalDateTime.now())
                .build();

        activeDebt = Debt.builder()
                .id(1L)
                .shop(shop)
                .client(client)
                .seller(seller)
                .totalAmount(BigDecimal.valueOf(100_000))
                .paidAmount(BigDecimal.valueOf(40_000))
                .status(Debt.DebtStatus.ACTIVE)
                .dueDate(LocalDate.now().plusDays(10))
                .createdAt(LocalDateTime.now())
                .build();

        paidDebt = Debt.builder()
                .id(2L)
                .shop(shop)
                .client(client)
                .seller(seller)
                .totalAmount(BigDecimal.valueOf(50_000))
                .paidAmount(BigDecimal.valueOf(50_000))
                .status(Debt.DebtStatus.PAID)
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();
    }

    @Test
    void shopWorkbook_hasThreeSheetsWithHeadersAndExpectedRowCounts() throws Exception {
        when(debtService.findByShop(shop)).thenReturn(List.of(activeDebt, paidDebt));

        ShopMembership membership = ShopMembership.builder()
                .id(1L)
                .client(client)
                .shop(shop)
                .status(ShopMembership.MembershipStatus.ACCEPTED)
                .createdAt(LocalDateTime.now())
                .build();
        when(membershipService.acceptedMembers(shop)).thenReturn(List.of(membership));
        when(debtService.findActiveByClient(client)).thenReturn(List.of(activeDebt));

        Payment payment = Payment.builder()
                .id(1L)
                .debt(activeDebt)
                .amount(BigDecimal.valueOf(40_000))
                .paidAt(LocalDateTime.now().minusDays(1))
                .recordedBy(300L)
                .build();
        when(paymentRepository.findByDebt(activeDebt)).thenReturn(List.of(payment));
        when(paymentRepository.findByDebt(paidDebt)).thenReturn(List.of());

        byte[] bytes = service.shopWorkbook(shop, Lang.UZ);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(3, wb.getNumberOfSheets());

            // Sheet 1: Debts — header + 2 debt rows + 1 total row
            Sheet debtsSheet = wb.getSheetAt(0);
            assertNotNull(debtsSheet.getRow(0));
            assertFalse(debtsSheet.getRow(0).getCell(0).getStringCellValue().isBlank());
            assertEquals(9, debtsSheet.getRow(0).getLastCellNum());
            assertEquals(3, debtsSheet.getLastRowNum());

            // Sheet 2: Clients — header + 1 member row
            Sheet clientsSheet = wb.getSheetAt(1);
            assertNotNull(clientsSheet.getRow(0));
            assertEquals(4, clientsSheet.getRow(0).getLastCellNum());
            assertEquals(1, clientsSheet.getLastRowNum());

            // Sheet 3: Payments (last 90 days) — header + 1 payment row
            Sheet paymentsSheet = wb.getSheetAt(2);
            assertNotNull(paymentsSheet.getRow(0));
            assertEquals(5, paymentsSheet.getRow(0).getLastCellNum());
            assertEquals(1, paymentsSheet.getLastRowNum());
        }
    }

    @Test
    void adminWorkbook_hasShopsSheetAndIsNullSafeForMissingSums() throws Exception {
        when(debtRepository.countActiveByShop(shop)).thenReturn(2L);
        when(debtRepository.totalActiveDebtByShop(shop)).thenReturn(BigDecimal.valueOf(60_000));
        when(debtRepository.countOverdueByShop(eq(shop), any(LocalDate.class))).thenReturn(0L);
        // Deliberately null to exercise the null-safety fallback to BigDecimal.ZERO.
        when(debtRepository.sumOverdueByShop(eq(shop), any(LocalDate.class))).thenReturn(null);

        byte[] bytes = service.adminWorkbook(List.of(shop), Lang.UZ);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());

            Sheet shopsSheet = wb.getSheetAt(0);
            assertNotNull(shopsSheet.getRow(0));
            assertEquals(7, shopsSheet.getRow(0).getLastCellNum());
            assertEquals(1, shopsSheet.getLastRowNum());
        }
    }
}
