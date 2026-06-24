package com.qarzbot.web;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Role;
import com.qarzbot.entity.Shop;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ShopService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.MessageFormatter;
import com.qarzbot.web.dto.DebtRow;
import com.qarzbot.web.dto.ShopRow;
import com.qarzbot.web.dto.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ShopService shopService;
    private final UserService userService;
    private final DebtService debtService;
    private final MembershipService membershipService;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<BotUser> users = userService.findAll();
        List<Shop> shops = shopService.findAll();

        long shopCount = shops.size();
        long userCount = users.size();
        long sellerCount = users.stream().filter(u -> u.getRole() == Role.SELLER).count();
        long clientCount = users.stream().filter(u -> u.getRole() == Role.CLIENT).count();

        long activeDebtCount = 0;
        BigDecimal totalDebtAmount = BigDecimal.ZERO;
        for (Shop shop : shops) {
            activeDebtCount += debtService.findActiveByShop(shop).size();
            BigDecimal shopDebt = debtService.totalActiveDebt(shop);
            if (shopDebt != null) {
                totalDebtAmount = totalDebtAmount.add(shopDebt);
            }
        }

        return Map.of(
                "shopCount", shopCount,
                "userCount", userCount,
                "sellerCount", sellerCount,
                "clientCount", clientCount,
                "activeDebtCount", activeDebtCount,
                "totalDebt", MessageFormatter.money(totalDebtAmount)
        );
    }

    @GetMapping("/shops")
    public List<ShopRow> shops() {
        List<Shop> allShops = shopService.findAll();
        List<ShopRow> rows = new ArrayList<>();

        for (Shop shop : allShops) {
            String address = shop.getAddress() != null ? shop.getAddress() : "—";
            BigDecimal totalDebt = debtService.totalActiveDebt(shop);
            String totalDebtStr = MessageFormatter.money(totalDebt != null ? totalDebt : BigDecimal.ZERO);
            long memberCount = membershipService.acceptedMembers(shop).size();
            rows.add(new ShopRow(shop.getId(), shop.getName(), address, totalDebtStr, memberCount));
        }

        return rows;
    }

    @GetMapping("/users")
    public List<UserRow> users() {
        List<BotUser> allUsers = userService.findAll();

        return allUsers.stream().map(u -> new UserRow(
                u.getTelegramId(),
                u.getFullName(),
                u.getUsername() != null ? u.getUsername() : "—",
                u.getPhoneNumber() != null ? u.getPhoneNumber() : "—",
                u.getRole().name(),
                u.getShop() != null ? u.getShop().getName() : "—"
        )).collect(Collectors.toList());
    }

    @GetMapping("/debts")
    public List<DebtRow> debts() {
        List<Shop> allShops = shopService.findAll();
        List<DebtRow> rows = new ArrayList<>();

        for (Shop shop : allShops) {
            List<Debt> activeDebts = debtService.findActiveByShop(shop);
            for (Debt d : activeDebts) {
                String dueDate = d.getDueDate() != null
                        ? d.getDueDate().format(DATE_FMT)
                        : "—";
                rows.add(new DebtRow(
                        d.getId(),
                        d.getClient().getFullName(),
                        d.getShop().getName(),
                        MessageFormatter.money(d.getTotalAmount()),
                        MessageFormatter.money(d.getPaidAmount()),
                        MessageFormatter.money(d.getRemainingAmount()),
                        d.getStatus().name(),
                        dueDate
                ));
            }
        }

        return rows;
    }
}
