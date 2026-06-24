package com.qarzbot.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShopRow {
    private Long id;
    private String name;
    private String address;
    private String totalDebt;
    private long memberCount;
}
