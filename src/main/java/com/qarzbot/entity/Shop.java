package com.qarzbot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phoneNumber;

    @Column(nullable = false)
    private Long ownerTelegramId;

    @Column(length = 8)
    @Builder.Default
    private String currency = "UZS";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Builder, no-args constructor va barcha holatlarni qamrab olish uchun JPA lifecycle callback
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
