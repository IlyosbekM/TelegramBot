package com.qarzbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qarzbot.entity.AuditLog;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Installment;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Product;
import com.qarzbot.entity.Reminder;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.entity.ShopRequest;
import com.qarzbot.repository.AuditLogRepository;
import com.qarzbot.repository.BotUserRepository;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.InstallmentRepository;
import com.qarzbot.repository.PaymentRepository;
import com.qarzbot.repository.PaymentRequestRepository;
import com.qarzbot.repository.ProductRepository;
import com.qarzbot.repository.ReminderRepository;
import com.qarzbot.repository.ShopMembershipRepository;
import com.qarzbot.repository.ShopRepository;
import com.qarzbot.repository.ShopRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Butun tizimning to'liq JSON zaxira nusxasini (backup) yaratadi, ZIP arxivga o'raydi.
 * Faqat o'qiydi (read-only) — hech qanday DB yozuvini o'zgartirmaydi.
 * Barcha bog'liqliklar (relations) faqat ID sifatida, barcha vaqt/enum qiymatlari
 * toString()/name() orqali qatorga aylantiriladi — shu sabab JavaTime moduli shart emas.
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final String ENTRY_NAME = "qarzbot-backup.json";

    private final BotUserRepository botUserRepository;
    private final ShopRepository shopRepository;
    private final DebtRepository debtRepository;
    private final PaymentRepository paymentRepository;
    private final ShopMembershipRepository shopMembershipRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final InstallmentRepository installmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final ShopRequestRepository shopRequestRepository;
    private final ProductRepository productRepository;
    private final ReminderRepository reminderRepository;

    @Transactional(readOnly = true)
    public byte[] fullBackupZip() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("exportedAt", LocalDateTime.now().toString());
            root.put("users", buildUsers());
            root.put("shops", buildShops());
            root.put("debts", buildDebts());
            root.put("payments", buildPayments());
            root.put("installments", buildInstallments());
            root.put("memberships", buildMemberships());
            root.put("paymentRequests", buildPaymentRequests());
            root.put("auditLogs", buildAuditLogs());
            root.put("shopRequests", buildShopRequests());
            root.put("products", buildProducts());
            root.put("reminders", buildReminders());

            byte[] json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(root);

            ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                zos.putNextEntry(new ZipEntry(ENTRY_NAME));
                zos.write(json);
                zos.closeEntry();
            }
            return zipBos.toByteArray();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Zaxira nusxa uchun JSON yaratib bo'lmadi: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Zaxira nusxa ZIP arxivini yaratib bo'lmadi: " + e.getMessage(), e);
        }
    }

    // ── Har bir jadval uchun qo'lda xarita qurish (ID'lar, string'lashtirilgan vaqt/enum) ──

    private List<Map<String, Object>> buildUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BotUser u : botUserRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("telegramId", u.getTelegramId());
            m.put("fullName", u.getFullName());
            m.put("phoneNumber", u.getPhoneNumber());
            m.put("username", u.getUsername());
            m.put("role", u.getRole() == null ? null : u.getRole().name());
            m.put("shopId", u.getShop() == null ? null : u.getShop().getId());
            m.put("createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
            m.put("language", u.getLanguage());
            m.put("remindersEnabled", u.getRemindersEnabled());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildShops() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Shop s : shopRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("address", s.getAddress());
            m.put("phoneNumber", s.getPhoneNumber());
            m.put("ownerTelegramId", s.getOwnerTelegramId());
            m.put("currency", s.getCurrency());
            m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildDebts() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Debt d : debtRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("shopId", d.getShop() == null ? null : d.getShop().getId());
            m.put("clientId", d.getClient() == null ? null : d.getClient().getTelegramId());
            m.put("sellerId", d.getSeller() == null ? null : d.getSeller().getTelegramId());
            m.put("totalAmount", d.getTotalAmount());
            m.put("paidAmount", d.getPaidAmount());
            m.put("description", d.getDescription());
            m.put("dueDate", d.getDueDate() == null ? null : d.getDueDate().toString());
            m.put("status", d.getStatus() == null ? null : d.getStatus().name());
            m.put("createdAt", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildPayments() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Payment p : paymentRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("debtId", p.getDebt() == null ? null : p.getDebt().getId());
            m.put("amount", p.getAmount());
            m.put("note", p.getNote());
            m.put("paidAt", p.getPaidAt() == null ? null : p.getPaidAt().toString());
            m.put("recordedBy", p.getRecordedBy());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildInstallments() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Installment i : installmentRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("debtId", i.getDebt() == null ? null : i.getDebt().getId());
            m.put("seqNo", i.getSeqNo());
            m.put("dueDate", i.getDueDate() == null ? null : i.getDueDate().toString());
            m.put("amount", i.getAmount());
            m.put("paid", i.isPaid());
            m.put("paidDate", i.getPaidDate() == null ? null : i.getPaidDate().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildMemberships() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopMembership sm : shopMembershipRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sm.getId());
            m.put("clientId", sm.getClient() == null ? null : sm.getClient().getTelegramId());
            m.put("shopId", sm.getShop() == null ? null : sm.getShop().getId());
            m.put("status", sm.getStatus() == null ? null : sm.getStatus().name());
            m.put("createdAt", sm.getCreatedAt() == null ? null : sm.getCreatedAt().toString());
            m.put("decidedAt", sm.getDecidedAt() == null ? null : sm.getDecidedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildPaymentRequests() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PaymentRequest pr : paymentRequestRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pr.getId());
            m.put("debtId", pr.getDebt() == null ? null : pr.getDebt().getId());
            m.put("amount", pr.getAmount());
            m.put("status", pr.getStatus() == null ? null : pr.getStatus().name());
            m.put("createdAt", pr.getCreatedAt() == null ? null : pr.getCreatedAt().toString());
            m.put("decidedAt", pr.getDecidedAt() == null ? null : pr.getDecidedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildAuditLogs() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AuditLog a : auditLogRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("shopId", a.getShop() == null ? null : a.getShop().getId());
            m.put("actorTelegramId", a.getActorTelegramId());
            m.put("action", a.getAction());
            m.put("debtId", a.getDebtId());
            m.put("details", a.getDetails());
            m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildShopRequests() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShopRequest sr : shopRequestRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sr.getId());
            m.put("requesterTelegramId", sr.getRequesterTelegramId());
            m.put("name", sr.getName());
            m.put("address", sr.getAddress());
            m.put("status", sr.getStatus() == null ? null : sr.getStatus().name());
            m.put("rejectReason", sr.getRejectReason());
            m.put("createdAt", sr.getCreatedAt() == null ? null : sr.getCreatedAt().toString());
            m.put("decidedAt", sr.getDecidedAt() == null ? null : sr.getDecidedAt().toString());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildProducts() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Product p : productRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("price", p.getPrice());
            m.put("unit", p.getUnit());
            m.put("stock", p.getStock());
            m.put("shopId", p.getShop() == null ? null : p.getShop().getId());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildReminders() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Reminder r : reminderRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("debtId", r.getDebt() == null ? null : r.getDebt().getId());
            m.put("sendAt", r.getSendAt() == null ? null : r.getSendAt().toString());
            m.put("sent", r.isSent());
            m.put("message", r.getMessage());
            m.put("sentAt", r.getSentAt() == null ? null : r.getSentAt().toString());
            list.add(m);
        }
        return list;
    }
}
