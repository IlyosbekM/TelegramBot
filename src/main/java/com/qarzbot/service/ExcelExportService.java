package com.qarzbot.service;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Excel (.xlsx) hisobot generatsiya qiluvchi servis (Apache POI, XSSFWorkbook).
 * Sotuvchi uchun do'kon bo'yicha 3 varaqli hisobot va admin uchun barcha
 * do'konlar bo'yicha bitta varaqli hisobot yaratadi.
 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int PAYMENTS_LOOKBACK_DAYS = 90;

    private final DebtService debtService;
    private final MembershipService membershipService;
    private final PaymentRepository paymentRepository;
    private final DebtRepository debtRepository;
    private final Loc loc;

    // ── Ommaviy API ───────────────────────────────────────────────────────────

    /**
     * Bitta do'kon bo'yicha to'liq Excel hisobot: Qarzlar / Mijozlar / To'lovlar varaqlari.
     */
    public byte[] shopWorkbook(Shop shop, Lang lang) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);

            buildDebtsSheet(wb, headerStyle, shop, lang);
            buildClientsSheet(wb, headerStyle, shop, lang);
            buildPaymentsSheet(wb, headerStyle, shop, lang);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel fayl yaratib bo'lmadi: " + e.getMessage(), e);
        }
    }

    /**
     * Admin uchun barcha do'konlar bo'yicha bitta varaqli umumiy hisobot.
     */
    public byte[] adminWorkbook(List<Shop> shops, Lang lang) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);

            buildShopsSheet(wb, headerStyle, shops, lang);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel fayl yaratib bo'lmadi: " + e.getMessage(), e);
        }
    }

    // ── Sheet 1: Qarzlar ─────────────────────────────────────────────────────

    private void buildDebtsSheet(Workbook wb, CellStyle headerStyle, Shop shop, Lang lang) {
        Sheet sheet = wb.createSheet(loc.t(lang, "excel.sheet_debts"));
        String currency = shop.getCurrency();

        String[] headers = {
                loc.t(lang, "excel.col_id"),
                loc.t(lang, "excel.col_client"),
                loc.t(lang, "excel.col_phone"),
                loc.t(lang, "excel.col_total", currency),
                loc.t(lang, "excel.col_paid", currency),
                loc.t(lang, "excel.col_remaining", currency),
                loc.t(lang, "excel.col_due"),
                loc.t(lang, "excel.col_status"),
                loc.t(lang, "excel.col_created")
        };
        writeHeaderRow(sheet, headerStyle, headers);

        List<Debt> debts = new ArrayList<>(debtService.findByShop(shop));
        debts.sort(Comparator
                .comparing((Debt d) -> activeGroupRank(d.getStatus()))
                .thenComparing(Debt::getCreatedAt, Comparator.reverseOrder()));

        int rowIdx = 1;
        BigDecimal activeRemainingSum = BigDecimal.ZERO;
        for (Debt d : debts) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(d.getId());
            row.createCell(1).setCellValue(d.getClient() != null ? nullSafe(d.getClient().getFullName()) : "-");
            row.createCell(2).setCellValue(d.getClient() != null ? nullSafe(d.getClient().getPhoneNumber()) : "-");
            row.createCell(3).setCellValue(d.getTotalAmount() != null ? d.getTotalAmount().doubleValue() : 0d);
            row.createCell(4).setCellValue(d.getPaidAmount() != null ? d.getPaidAmount().doubleValue() : 0d);
            row.createCell(5).setCellValue(d.getRemainingAmount() != null ? d.getRemainingAmount().doubleValue() : 0d);
            row.createCell(6).setCellValue(d.getDueDate() != null ? d.getDueDate().format(DATE_FMT) : "-");
            row.createCell(7).setCellValue(MessageFormatter.statusLabel(d.getStatus(), lang));
            row.createCell(8).setCellValue(d.getCreatedAt() != null ? d.getCreatedAt().format(DATE_FMT) : "-");

            if (isActiveGroup(d.getStatus()) && d.getRemainingAmount() != null) {
                activeRemainingSum = activeRemainingSum.add(d.getRemainingAmount());
            }
        }

        Row totalRow = sheet.createRow(rowIdx);
        Cell totalLabel = totalRow.createCell(0);
        totalLabel.setCellValue(loc.t(lang, "excel.total_row"));
        totalLabel.setCellStyle(headerStyle);
        Cell totalValue = totalRow.createCell(5);
        totalValue.setCellValue(activeRemainingSum.doubleValue());
        totalValue.setCellStyle(headerStyle);

        autoSize(sheet, headers.length);
    }

    private boolean isActiveGroup(Debt.DebtStatus status) {
        return status == Debt.DebtStatus.ACTIVE || status == Debt.DebtStatus.OVERDUE;
    }

    private int activeGroupRank(Debt.DebtStatus status) {
        return isActiveGroup(status) ? 0 : 1;
    }

    // ── Sheet 2: Mijozlar ────────────────────────────────────────────────────

    private void buildClientsSheet(Workbook wb, CellStyle headerStyle, Shop shop, Lang lang) {
        Sheet sheet = wb.createSheet(loc.t(lang, "excel.sheet_clients"));

        String[] headers = {
                loc.t(lang, "excel.col_client"),
                loc.t(lang, "excel.col_phone"),
                loc.t(lang, "excel.col_debt_count"),
                loc.t(lang, "excel.col_active_balance", shop.getCurrency())
        };
        writeHeaderRow(sheet, headerStyle, headers);

        List<ShopMembership> members = membershipService.acceptedMembers(shop);
        int rowIdx = 1;
        for (ShopMembership m : members) {
            BotUser client = m.getClient();
            List<Debt> activeInShop = debtService.findActiveByClient(client).stream()
                    .filter(d -> d.getShop() != null && d.getShop().getId().equals(shop.getId()))
                    .toList();

            BigDecimal balance = BigDecimal.ZERO;
            for (Debt d : activeInShop) {
                if (d.getRemainingAmount() != null) {
                    balance = balance.add(d.getRemainingAmount());
                }
            }

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(nullSafe(client.getFullName()));
            row.createCell(1).setCellValue(nullSafe(client.getPhoneNumber()));
            row.createCell(2).setCellValue(activeInShop.size());
            row.createCell(3).setCellValue(balance.doubleValue());
        }

        autoSize(sheet, headers.length);
    }

    // ── Sheet 3: To'lovlar (oxirgi 90 kun) ───────────────────────────────────

    private void buildPaymentsSheet(Workbook wb, CellStyle headerStyle, Shop shop, Lang lang) {
        Sheet sheet = wb.createSheet(loc.t(lang, "excel.sheet_payments"));

        String[] headers = {
                loc.t(lang, "excel.col_datetime"),
                loc.t(lang, "excel.col_client"),
                loc.t(lang, "excel.col_debt_id"),
                loc.t(lang, "excel.col_amount", shop.getCurrency()),
                loc.t(lang, "excel.col_recorded_by")
        };
        writeHeaderRow(sheet, headerStyle, headers);

        LocalDateTime since = LocalDateTime.now().minusDays(PAYMENTS_LOOKBACK_DAYS);
        List<Debt> debts = debtService.findByShop(shop);

        List<PaidRow> paidRows = new ArrayList<>();
        for (Debt d : debts) {
            for (Payment p : paymentRepository.findByDebt(d)) {
                if (p.getPaidAt() != null && !p.getPaidAt().isBefore(since)) {
                    paidRows.add(new PaidRow(d, p));
                }
            }
        }
        paidRows.sort((a, b) -> b.payment.getPaidAt().compareTo(a.payment.getPaidAt()));

        int rowIdx = 1;
        for (PaidRow pr : paidRows) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(pr.payment.getPaidAt().format(DATETIME_FMT));
            row.createCell(1).setCellValue(pr.debt.getClient() != null ? nullSafe(pr.debt.getClient().getFullName()) : "-");
            row.createCell(2).setCellValue(pr.debt.getId());
            row.createCell(3).setCellValue(pr.payment.getAmount() != null ? pr.payment.getAmount().doubleValue() : 0d);
            row.createCell(4).setCellValue(pr.payment.getRecordedBy() != null ? pr.payment.getRecordedBy() : 0L);
        }

        autoSize(sheet, headers.length);
    }

    /** Bitta to'lov qatori — tegishli qarz bilan birga (lazy-load muammosisiz, chunki debt allaqachon fetch qilingan). */
    private static final class PaidRow {
        final Debt debt;
        final Payment payment;

        PaidRow(Debt debt, Payment payment) {
            this.debt = debt;
            this.payment = payment;
        }
    }

    // ── Sheet (admin): Do'konlar ─────────────────────────────────────────────

    private void buildShopsSheet(Workbook wb, CellStyle headerStyle, List<Shop> shops, Lang lang) {
        Sheet sheet = wb.createSheet(loc.t(lang, "excel.sheet_shops"));

        String[] headers = {
                loc.t(lang, "excel.col_shop_name"),
                loc.t(lang, "excel.col_address"),
                loc.t(lang, "excel.col_currency"),
                loc.t(lang, "excel.col_active_count"),
                loc.t(lang, "excel.col_total_remaining"),
                loc.t(lang, "excel.col_overdue_count"),
                loc.t(lang, "excel.col_overdue_sum")
        };
        writeHeaderRow(sheet, headerStyle, headers);

        LocalDate today = LocalDate.now();
        int rowIdx = 1;
        for (Shop shop : shops) {
            long activeCount = debtRepository.countActiveByShop(shop);
            BigDecimal totalRemaining = debtRepository.totalActiveDebtByShop(shop);
            if (totalRemaining == null) {
                totalRemaining = BigDecimal.ZERO;
            }
            long overdueCount = debtRepository.countOverdueByShop(shop, today);
            BigDecimal overdueSum = debtRepository.sumOverdueByShop(shop, today);
            if (overdueSum == null) {
                overdueSum = BigDecimal.ZERO;
            }

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(nullSafe(shop.getName()));
            row.createCell(1).setCellValue(nullSafe(shop.getAddress()));
            row.createCell(2).setCellValue(nullSafe(shop.getCurrency()));
            row.createCell(3).setCellValue(activeCount);
            row.createCell(4).setCellValue(totalRemaining.doubleValue());
            row.createCell(5).setCellValue(overdueCount);
            row.createCell(6).setCellValue(overdueSum.doubleValue());
        }

        autoSize(sheet, headers.length);
    }

    // ── Umumiy yordamchilar ──────────────────────────────────────────────────

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeHeaderRow(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
