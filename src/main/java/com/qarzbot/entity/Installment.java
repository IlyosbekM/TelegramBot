package com.qarzbot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "installment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Installment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    private int seqNo;

    private LocalDate dueDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    private boolean paid = false;

    private LocalDate paidDate;
}
