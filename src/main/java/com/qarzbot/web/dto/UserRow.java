package com.qarzbot.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserRow {
    private Long telegramId;
    private String fullName;
    private String username;
    private String phone;
    private String role;
    private String shopName;
}
