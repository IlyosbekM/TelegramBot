package com.qarzbot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bot_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotUser {

    @Id
    private Long telegramId;

    @Column(nullable = false)
    private String fullName;

    private String phoneNumber;

    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String state; // for FSM (conversation state)

    @Builder.Default
    private Boolean remindersEnabled = true;

    @Column(length = 8)
    private String language; // i18n: "uz"/"ru"/"en"; default kodda qo'llaniladi (Lang.UZ)

    // Builder, no-args constructor va barcha holatlarni qamrab olish uchun JPA lifecycle callback
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
