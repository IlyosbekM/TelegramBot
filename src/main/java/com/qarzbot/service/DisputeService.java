package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Dispute;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.DebtRepository;
import com.qarzbot.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Business logic for the debt dispute ("e'tiroz") feature: a CLIENT can dispute an
 * ACTIVE/OVERDUE debt with a reason; the shop's SELLER(s) then accept (and fix the
 * debt manually via existing edit tools) or reject (with a reason relayed back).
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final DebtRepository debtRepository;

    @Transactional
    public Dispute open(Long debtId, Long clientTelegramId, String reason) {
        Debt debt = debtRepository.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        if (!debt.getClient().getTelegramId().equals(clientTelegramId)) {
            throw new IllegalArgumentException("Bu qarz sizga tegishli emas");
        }
        if (debt.getStatus() != Debt.DebtStatus.ACTIVE && debt.getStatus() != Debt.DebtStatus.OVERDUE) {
            throw new IllegalArgumentException("Bu qarz bo'yicha e'tiroz bildirib bo'lmaydi");
        }
        if (disputeRepository.existsByDebtAndStatus(debt, Dispute.DisputeStatus.OPEN)) {
            throw new IllegalArgumentException("Bu qarz bo'yicha ochiq e'tiroz allaqachon bor");
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 3 || trimmed.length() > 400) {
            throw new IllegalArgumentException("Sabab 3 dan 400 gacha belgidan iborat bo'lsin");
        }
        Dispute dispute = Dispute.builder()
                .debt(debt)
                .client(debt.getClient())
                .reason(trimmed)
                .status(Dispute.DisputeStatus.OPEN)
                .build();
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute accept(Long disputeId, Shop sellerShop) {
        Dispute dispute = disputeRepository.findByIdFetched(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("E'tiroz topilmadi"));
        if (dispute.getStatus() != Dispute.DisputeStatus.OPEN) {
            throw new IllegalArgumentException("E'tiroz allaqachon ko'rib chiqilgan");
        }
        if (!dispute.getDebt().getShop().getId().equals(sellerShop.getId())) {
            throw new IllegalArgumentException("Bu e'tiroz sizning do'koningizga tegishli emas");
        }
        dispute.setStatus(Dispute.DisputeStatus.ACCEPTED);
        dispute.setDecidedAt(LocalDateTime.now());
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute reject(Long disputeId, Shop sellerShop, String note) {
        Dispute dispute = disputeRepository.findByIdFetched(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("E'tiroz topilmadi"));
        if (dispute.getStatus() != Dispute.DisputeStatus.OPEN) {
            throw new IllegalArgumentException("E'tiroz allaqachon ko'rib chiqilgan");
        }
        if (!dispute.getDebt().getShop().getId().equals(sellerShop.getId())) {
            throw new IllegalArgumentException("Bu e'tiroz sizning do'koningizga tegishli emas");
        }
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() > 400) {
            trimmed = trimmed.substring(0, 400);
        }
        dispute.setStatus(Dispute.DisputeStatus.REJECTED);
        dispute.setResolutionNote(trimmed);
        dispute.setDecidedAt(LocalDateTime.now());
        return disputeRepository.save(dispute);
    }

    public List<Dispute> openForShop(Shop shop) {
        return disputeRepository.findOpenByShopFetched(shop, Dispute.DisputeStatus.OPEN);
    }
}
