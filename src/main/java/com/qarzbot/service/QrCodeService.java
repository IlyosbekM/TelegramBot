package com.qarzbot.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.qarzbot.entity.Debt;
import com.qarzbot.util.MessageFormatter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * QR kod generatsiyasi: qarz ma'lumotlarini PNG baytlariga aylantiradi.
 */
@Service
public class QrCodeService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Berilgan matn asosida {@code size x size} piksellik QR PNG baytlarini qaytaradi.
     *
     * @param content QR kodga yoziladigan matn
     * @param size    piksel o'lchami (kenglik = balandlik)
     * @return PNG tasvirining bayt massivi
     * @throws RuntimeException WriterException yoki IOException bo'lsa
     */
    public byte[] qrPng(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("QR kod yaratishda xato: " + e.getMessage(), e);
        }
    }

    /**
     * Qarz asosida inson o'qiy oladigan qisqa matn tuzadi (QR ichiga yoziladi).
     * Telegram uchun eng kichik hajm — ixcham formatda.
     *
     * @param debt to'liq yuklangan Debt obyekti
     * @return ko'p qatorli matn satri
     */
    public String buildDebtSummary(Debt debt) {
        String dueDate = debt.getDueDate() != null
                ? debt.getDueDate().format(DATE_FMT)
                : "-";

        return "QarzBot\n"
                + "ID: #" + debt.getId() + "\n"
                + "Do'kon: " + debt.getShop().getName() + "\n"
                + "Mijoz: " + debt.getClient().getFullName() + "\n"
                + "Qoldiq: " + MessageFormatter.money(debt.getRemainingAmount()) + "\n"
                + "Muddat: " + dueDate;
    }
}
