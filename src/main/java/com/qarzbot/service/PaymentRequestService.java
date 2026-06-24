package com.qarzbot.service;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentRequestService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final DebtService debtService;

    @Transactional
    public PaymentRequest create(Long debtId, BigDecimal amount) {
        Debt debt = debtService.findByIdFetched(debtId)
                .orElseThrow(() -> new IllegalArgumentException("Qarz topilmadi"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Summa musbat bo'lishi kerak");
        }
        if (amount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new IllegalArgumentException("To'lov summasi qoldiqdan oshib ketdi");
        }
        PaymentRequest request = PaymentRequest.builder()
                .debt(debt)
                .amount(amount)
                .build();
        return paymentRequestRepository.save(request);
    }

    @Transactional
    public Payment confirm(Long requestId, Long sellerTelegramId) {
        PaymentRequest req = paymentRequestRepository.findByIdFetched(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        if (req.getStatus() != PaymentRequest.PaymentRequestStatus.PENDING) {
            throw new IllegalStateException("So'rov allaqachon ko'rib chiqilgan");
        }
        Payment payment = debtService.addPayment(
                req.getDebt().getId(),
                req.getAmount(),
                "Mijoz to'lovi (tasdiqlangan)",
                sellerTelegramId
        );
        req.setStatus(PaymentRequest.PaymentRequestStatus.CONFIRMED);
        req.setDecidedAt(LocalDateTime.now());
        paymentRequestRepository.save(req);
        return payment;
    }

    @Transactional
    public PaymentRequest reject(Long requestId) {
        PaymentRequest req = paymentRequestRepository.findByIdFetched(requestId)
                .orElseThrow(() -> new IllegalArgumentException("So'rov topilmadi"));
        req.setStatus(PaymentRequest.PaymentRequestStatus.REJECTED);
        req.setDecidedAt(LocalDateTime.now());
        return paymentRequestRepository.save(req);
    }

    public List<PaymentRequest> pendingForShop(Shop shop) {
        return paymentRequestRepository.findByShopAndStatusFetched(shop, PaymentRequest.PaymentRequestStatus.PENDING);
    }

    public Optional<PaymentRequest> findByIdFetched(Long id) {
        return paymentRequestRepository.findByIdFetched(id);
    }
}
