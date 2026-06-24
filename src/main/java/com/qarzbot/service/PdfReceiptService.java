package com.qarzbot.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.util.MessageFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF cheklar va hisobotlar generatsiya qiluvchi servis.
 * OpenPDF (com.lowagie.text.*) ishlatadi.
 * Ichki Helvetica shrift ASCII/Lotin (Uzbek-Latin apostroflar) uchun yetarli.
 */
@Slf4j
@Service
public class PdfReceiptService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ── Shrift yordamchilari ──────────────────────────────────────────────────

    private Font headerFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD);
    }

    private Font subHeaderFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD);
    }

    private Font normalFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL);
    }

    private Font smallFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);
    }

    private Font boldFont() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD);
    }

    // ── Ustun sarlavhalari (til bo'yicha) ──────────────────────────────────────

    private String[] tableHeaders(Lang lang) {
        return switch (lang) {
            case RU -> new String[]{"ID", "Сумма", "Оплачено", "Остаток", "Срок", "Статус"};
            case EN -> new String[]{"ID", "Total", "Paid", "Remaining", "Due", "Status"};
            default -> new String[]{"ID", "Jami", "To'landi", "Qoldiq", "Muddat", "Holat"};
        };
    }

    private String labelShop(Lang lang) {
        return switch (lang) {
            case RU -> "Магазин";
            case EN -> "Shop";
            default -> "Do'kon";
        };
    }

    private String labelClient(Lang lang) {
        return switch (lang) {
            case RU -> "Клиент";
            case EN -> "Client";
            default -> "Mijoz";
        };
    }

    private String labelDate(Lang lang) {
        return switch (lang) {
            case RU -> "Дата";
            case EN -> "Date";
            default -> "Sana";
        };
    }

    private String labelDebtId(Lang lang) {
        return switch (lang) {
            case RU -> "Qarz ID";
            case EN -> "Debt ID";
            default -> "Qarz ID";
        };
    }

    private String labelDescription(Lang lang) {
        return switch (lang) {
            case RU -> "Описание";
            case EN -> "Description";
            default -> "Izoh";
        };
    }

    private String labelTotal(Lang lang) {
        return switch (lang) {
            case RU -> "Общая сумма";
            case EN -> "Total amount";
            default -> "Umumiy summa";
        };
    }

    private String labelPaid(Lang lang) {
        return switch (lang) {
            case RU -> "Оплачено";
            case EN -> "Paid";
            default -> "To'landi";
        };
    }

    private String labelRemaining(Lang lang) {
        return switch (lang) {
            case RU -> "Остаток";
            case EN -> "Remaining";
            default -> "Qoldiq";
        };
    }

    private String labelDue(Lang lang) {
        return switch (lang) {
            case RU -> "Срок";
            case EN -> "Due date";
            default -> "Muddat";
        };
    }

    private String labelStatus(Lang lang) {
        return switch (lang) {
            case RU -> "Статус";
            case EN -> "Status";
            default -> "Holat";
        };
    }

    private String labelTotalsRow(Lang lang) {
        return switch (lang) {
            case RU -> "ИТОГО (остаток)";
            case EN -> "TOTAL (remaining)";
            default -> "JAMI (qoldiq)";
        };
    }

    private String statementTitle(Lang lang) {
        return switch (lang) {
            case RU -> "ВЫПИСКА ПО ДОЛГАМ";
            case EN -> "DEBT STATEMENT";
            default -> "QARZ HISOBOTI";
        };
    }

    private String receiptTitle(Lang lang) {
        return switch (lang) {
            case RU -> "ЧЕК — ДОЛГОВАЯ РАСПИСКА";
            case EN -> "RECEIPT — DEBT RECORD";
            default -> "CHEK — QARZ HUJJATI";
        };
    }

    // ── Hujayra quriluvchilari ─────────────────────────────────────────────────

    private PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, boldFont()));
        cell.setBackgroundColor(new java.awt.Color(220, 220, 220));
        cell.setPadding(5f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell dataCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normalFont()));
        cell.setPadding(4f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private PdfPCell dataCellCenter(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normalFont()));
        cell.setPadding(4f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell totalCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, boldFont()));
        cell.setBackgroundColor(new java.awt.Color(240, 240, 200));
        cell.setPadding(5f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    // ── Asosiy metodlar ───────────────────────────────────────────────────────

    /**
     * Klientning do'kondagi barcha qarzlari bo'yicha hisobot (jadval ko'rinishida).
     * @param client  mijoz (BotUser)
     * @param shop    do'kon (null bo'lsa "—" ko'rsatiladi)
     * @param debts   qarzlar ro'yxati
     * @param lang    hisobot tili
     * @return PDF bayt massivi
     */
    public byte[] debtStatement(BotUser client, Shop shop, List<Debt> debts, Lang lang) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36f, 36f, 54f, 36f);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ---- Sarlavha ----
            Paragraph title = new Paragraph(statementTitle(lang), headerFont());
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6f);
            doc.add(title);

            // ---- Meta ma'lumotlar ----
            String shopName = (shop != null) ? shop.getName() : "—";
            String today = LocalDate.now().format(DATE_FMT);

            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100f);
            meta.setSpacingBefore(4f);
            meta.setSpacingAfter(10f);
            meta.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            meta.getDefaultCell().setPadding(3f);

            addMetaRow(meta, labelShop(lang), shopName);
            addMetaRow(meta, labelClient(lang), client.getFullName());
            addMetaRow(meta, labelDate(lang), today);
            doc.add(meta);

            // ---- Qarzlar jadvali ----
            String[] headers = tableHeaders(lang);
            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100f);
            // Ustun kengliklari: ID kichik, Status kichik, qolganlar teng
            table.setWidths(new float[]{0.7f, 2f, 2f, 2f, 1.8f, 2f});

            for (String h : headers) {
                table.addCell(headerCell(h));
            }

            BigDecimal totalRemaining = BigDecimal.ZERO;
            String currency = (shop != null) ? shop.getCurrency() : null;

            for (Debt d : debts) {
                String debtCurrency = (d.getShop() != null) ? d.getShop().getCurrency() : currency;

                table.addCell(dataCellCenter(String.valueOf(d.getId())));
                table.addCell(dataCell(MessageFormatter.money(d.getTotalAmount(), debtCurrency, lang)));
                table.addCell(dataCell(MessageFormatter.money(d.getPaidAmount(), debtCurrency, lang)));
                table.addCell(dataCell(MessageFormatter.money(d.getRemainingAmount(), debtCurrency, lang)));
                table.addCell(dataCellCenter(d.getDueDate() != null ? d.getDueDate().format(DATE_FMT) : "-"));
                table.addCell(dataCellCenter(MessageFormatter.statusLabel(d.getStatus(), lang)));

                totalRemaining = totalRemaining.add(d.getRemainingAmount());
            }

            // ---- Jami qator ----
            PdfPCell totalLabel = totalCell(labelTotalsRow(lang));
            totalLabel.setColspan(5);
            table.addCell(totalLabel);
            table.addCell(totalCell(MessageFormatter.money(totalRemaining, currency, lang)));

            doc.add(table);

        } catch (DocumentException e) {
            log.error("PDF hisobot yaratishda xato: {}", e.getMessage(), e);
            throw new RuntimeException("PDF hisobot yaratib bo'lmadi: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Bitta qarz uchun chek hujjati.
     * @param debt  qarz
     * @param lang  tili
     * @return PDF bayt massivi
     */
    public byte[] singleDebtReceipt(Debt debt, Lang lang) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 54f, 54f, 72f, 54f);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ---- Sarlavha ----
            Paragraph title = new Paragraph(receiptTitle(lang), headerFont());
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4f);
            doc.add(title);

            doc.add(new Paragraph("────────────────────────────────────────────────", smallFont()));

            // ---- Ma'lumotlar jadvali (2 ustun) ----
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100f);
            info.setSpacingBefore(6f);
            info.setSpacingAfter(10f);
            info.setWidths(new float[]{1.5f, 3f});
            info.getDefaultCell().setBorder(Rectangle.BOX);
            info.getDefaultCell().setPadding(5f);

            String shopName = (debt.getShop() != null) ? debt.getShop().getName() : "—";
            String clientName = (debt.getClient() != null) ? debt.getClient().getFullName() : "—";
            String currency = (debt.getShop() != null) ? debt.getShop().getCurrency() : null;

            addInfoRow(info, labelShop(lang), shopName);
            addInfoRow(info, labelClient(lang), clientName);
            addInfoRow(info, labelDate(lang), LocalDate.now().format(DATE_FMT));
            addInfoRow(info, labelDebtId(lang), "#" + debt.getId());

            if (debt.getDescription() != null && !debt.getDescription().isBlank()) {
                addInfoRow(info, labelDescription(lang), debt.getDescription());
            }

            addInfoRow(info, labelTotal(lang), MessageFormatter.money(debt.getTotalAmount(), currency, lang));
            addInfoRow(info, labelPaid(lang), MessageFormatter.money(debt.getPaidAmount(), currency, lang));
            addInfoRow(info, labelRemaining(lang), MessageFormatter.money(debt.getRemainingAmount(), currency, lang));
            addInfoRow(info, labelDue(lang), debt.getDueDate() != null ? debt.getDueDate().format(DATE_FMT) : "-");
            addInfoRow(info, labelStatus(lang), MessageFormatter.statusLabel(debt.getStatus(), lang));

            doc.add(info);

            doc.add(new Paragraph("────────────────────────────────────────────────", smallFont()));

        } catch (DocumentException e) {
            log.error("PDF chek yaratishda xato (qarz #{}): {}", debt.getId(), e.getMessage(), e);
            throw new RuntimeException("PDF chek yaratib bo'lmadi: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return baos.toByteArray();
    }

    // ── Ichki yordamchi metodlar ───────────────────────────────────────────────

    /** Meta jadvalga qator qo'shadi (chegara yo'q, label qalin). */
    private void addMetaRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label + ":", boldFont()));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(3f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, normalFont()));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(3f);
        table.addCell(valueCell);
    }

    /** Info jadvalga qator qo'shadi (chegara bor, label qalin). */
    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont()));
        labelCell.setPadding(5f);
        labelCell.setBackgroundColor(new java.awt.Color(245, 245, 245));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, normalFont()));
        valueCell.setPadding(5f);
        table.addCell(valueCell);
    }
}
