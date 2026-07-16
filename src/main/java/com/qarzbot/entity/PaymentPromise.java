package com.qarzbot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "To'lov va'dasi" (payment promise) — mijoz muayyan sanagacha qarzni to'lashga
 * va'da beradi. Sotuvchilar xabardor qilinadi; scheduler va'da kunida eslatadi
 * va ertasiga natijani (KEPT/BROKEN) baholaydi.
 */
@Entity
@Table(name = "payment_promise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentPromise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private BotUser client;

    @Column(nullable = false)
    private LocalDate promiseDate;

    /** Va'da berilgan paytdagi qarz qoldig'i ("muzlatilgan" summa). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PromiseStatus status = PromiseStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Va'da KEPT/BROKEN deb belgilangan payt (scheduler tomonidan to'ldiriladi). */
    private LocalDateTime decidedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum PromiseStatus {
        OPEN, KEPT, BROKEN
    }
}
