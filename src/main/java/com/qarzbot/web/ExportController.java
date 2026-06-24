package com.qarzbot.web;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.entity.ShopMembership;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // UTF-8 BOM so Excel renders Uzbek characters correctly
    private static final String BOM = "﻿";

    private final ShopService shopService;
    private final UserService userService;
    private final DebtService debtService;
    private final MembershipService membershipService;

    @GetMapping("/shops.csv")
    public ResponseEntity<byte[]> exportShops() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(csv("ID", "Nomi", "Manzil", "A'zolar", "Jami qarz"));

        List<Shop> shops = shopService.findAll();
        for (Shop shop : shops) {
            int memberCount = membershipService.acceptedMembers(shop).size();
            BigDecimal totalDebt = debtService.totalActiveDebt(shop);
            sb.append(csv(
                    String.valueOf(shop.getId()),
                    nullToEmpty(shop.getName()),
                    nullToEmpty(shop.getAddress()),
                    String.valueOf(memberCount),
                    MessageFormatter.money(totalDebt)
            ));
        }

        return buildResponse(sb.toString(), "shops.csv");
    }

    @GetMapping("/users.csv")
    public ResponseEntity<byte[]> exportUsers() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(csv("ID", "Ism", "Username", "Telefon", "Rol", "Do'kon"));

        List<BotUser> users = userService.findAll();
        for (BotUser user : users) {
            String shopName = (user.getShop() != null) ? user.getShop().getName() : "";
            sb.append(csv(
                    String.valueOf(user.getTelegramId()),
                    nullToEmpty(user.getFullName()),
                    nullToEmpty(user.getUsername()),
                    nullToEmpty(user.getPhoneNumber()),
                    user.getRole() != null ? user.getRole().name() : "",
                    nullToEmpty(shopName)
            ));
        }

        return buildResponse(sb.toString(), "users.csv");
    }

    @GetMapping("/debts.csv")
    public ResponseEntity<byte[]> exportDebts() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(csv("#", "Mijoz", "Do'kon", "Umumiy", "To'langan", "Qoldiq", "Holat", "Muddat"));

        List<Shop> shops = shopService.findAll();
        for (Shop shop : shops) {
            List<Debt> debts = debtService.findActiveByShop(shop);
            for (Debt debt : debts) {
                String dueDate = (debt.getDueDate() != null)
                        ? debt.getDueDate().format(DATE_FMT)
                        : "";
                sb.append(csv(
                        String.valueOf(debt.getId()),
                        nullToEmpty(debt.getClient().getFullName()),
                        nullToEmpty(debt.getShop().getName()),
                        MessageFormatter.money(debt.getTotalAmount()),
                        MessageFormatter.money(debt.getPaidAmount()),
                        MessageFormatter.money(debt.getRemainingAmount()),
                        debt.getStatus() != null ? debt.getStatus().name() : "",
                        dueDate
                ));
            }
        }

        return buildResponse(sb.toString(), "debts.csv");
    }

    // RFC 4180: wrap field in quotes if it contains comma, quote, or newline;
    // double any internal quotes.
    private String csvField(String value) {
        if (value == null) value = "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (needsQuoting) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String csv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(csvField(fields[i]));
        }
        sb.append("\n");
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private ResponseEntity<byte[]> buildResponse(String csvContent, String filename) {
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
