package com.qarzbot.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DebtRow {
    private Long id;
    private String clientName;
    private String shopName;
    private String total;
    private String paid;
    private String remaining;
    private String status;
    private String dueDate;
}
