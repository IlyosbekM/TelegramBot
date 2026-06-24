package com.qarzbot.handler.conversation;

import com.qarzbot.bot.BotMessenger;
import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import com.qarzbot.i18n.Lang;
import com.qarzbot.i18n.Loc;
import com.qarzbot.service.AuditService;
import com.qarzbot.service.DebtService;
import com.qarzbot.service.MembershipService;
import com.qarzbot.service.ProductService;
import com.qarzbot.service.UserService;
import com.qarzbot.util.KeyboardFactory;
import com.qarzbot.util.MessageFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SellerConversationFlow implements ConversationFlow {

    private final UserService userService;
    private final DebtService debtService;
    private final AuditService auditService;
    private final MembershipService membershipService;
    private final ProductService productService;
    private final BotMessenger messenger;
    private final Loc loc;

    @Override
    public boolean supports(String state) {
        return state != null && state.startsWith("SELLER_");
    }

    @Override
    public void handle(BotUser user, Update update) {
        Message msg = update.getMessage();
        String text = msg.getText();
        String state = user.getState();
        Lang lang = Lang.fromStored(user.getLanguage());

        // SELLER — qarz qo'shish (SELLER_ADD_DEBT_CLIENT_PHONE OLIB TASHLANDI)
        // Endi to'g'ridan-to'g'ri amount bosqichidan boshlanadi (adddebt: callback orqali)
        if (state.startsWith("SELLER_ADD_DEBT_AMOUNT:")) {
            Long clientId = Long.parseLong(state.substring("SELLER_ADD_DEBT_AMOUNT:".length()));
            // Quick one-line entry: summa, izoh, sana
            if (text.contains(",")) {
                String[] q = text.split(",", 3);
                BigDecimal amount;
                try {
                    amount = new BigDecimal(q[0].replaceAll("\\s+", ""));
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid_quick"));
                    return;
                }
                String description = (q.length > 1 && !q[1].trim().isEmpty()) ? q[1].trim() : null;
                LocalDate dueDate = null;
                if (q.length > 2 && !q[2].trim().isEmpty() && !"-".equals(q[2].trim())) {
                    try {
                        dueDate = LocalDate.parse(q[2].trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    } catch (Exception e) {
                        messenger.replyMarkdown(msg, loc.t(user, "err.date_invalid_retry"));
                        return;
                    }
                }
                finishDebtCreate(user, msg, clientId, amount, description, dueDate);
                return;
            }
            // Normal single-amount flow
            BigDecimal amount;
            try {
                amount = new BigDecimal(text.replaceAll("\\s+", ""));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid_positive"));
                return;
            }
            user.setState("SELLER_ADD_DEBT_DESC:" + clientId + ":" + amount.toPlainString());
            userService.save(user);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.debt_desc"));
            return;
        }
        if (state.startsWith("SELLER_ADD_DEBT_DESC:")) {
            String[] parts = state.substring("SELLER_ADD_DEBT_DESC:".length()).split(":");
            user.setState("SELLER_ADD_DEBT_DUE:" + parts[0] + ":" + parts[1] + ":" + ("-".equals(text) ? "" : text));
            userService.save(user);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.debt_due"));
            return;
        }
        if (state.startsWith("SELLER_ADD_DEBT_DUE:")) {
            String[] parts = state.substring("SELLER_ADD_DEBT_DUE:".length()).split(":", 3);
            Long clientId = Long.parseLong(parts[0]);
            BigDecimal amount = new BigDecimal(parts[1]);
            String description = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
            LocalDate dueDate = null;
            if (!"-".equals(text)) {
                try {
                    dueDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } catch (Exception e) {
                    messenger.replyMarkdown(msg, loc.t(user, "err.date_invalid"));
                    return;
                }
            }
            finishDebtCreate(user, msg, clientId, amount, description, dueDate);
            return;
        }

        // SELLER — to'lov qabul qilish (to'g'ridan-to'g'ri, eski oqim)
        if (state.equals("SELLER_PAYMENT_DEBT_ID")) {
            Long debtId;
            try {
                debtId = Long.parseLong(text.replace("#", "").trim());
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.id_invalid"));
                return;
            }
            Optional<Debt> debt = debtService.findById(debtId);
            if (debt.isEmpty()) {
                messenger.replyMarkdown(msg, loc.t(user, "err.debt_not_found"));
                userService.clearState(user.getTelegramId());
                return;
            }
            user.setState("SELLER_PAYMENT_AMOUNT:" + debtId);
            userService.save(user);
            messenger.replyMarkdown(msg, MessageFormatter.formatDebt(debt.get(), lang) + "\n" + loc.t(user, "prompt.payment_amount"));
            return;
        }
        if (state.startsWith("SELLER_PAYMENT_AMOUNT:")) {
            Long debtId = Long.parseLong(state.substring("SELLER_PAYMENT_AMOUNT:".length()));
            BigDecimal amount;
            try {
                amount = new BigDecimal(text.replaceAll("\\s+", ""));
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid"));
                return;
            }
            try {
                debtService.addPayment(debtId, amount, null, user.getTelegramId());
                Debt updated = debtService.findById(debtId).orElseThrow();
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "seller.payment_accepted", MessageFormatter.formatDebt(updated, lang)));

                // Klientga xabar (klient tilida)
                try {
                    Lang clientLang = Lang.fromStored(updated.getClient().getLanguage());
                    messenger.execute(SendMessage.builder()
                            .chatId(updated.getClient().getTelegramId().toString())
                            .text(MessageFormatter.formatPaymentReceipt(updated, amount, clientLang))
                            .parseMode("Markdown")
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage());
            }
            return;
        }

        // SELLER — qarzni oshirish
        if (state.startsWith("SELLER_INCREASE_AMOUNT:")) {
            Long debtId = Long.parseLong(state.substring("SELLER_INCREASE_AMOUNT:".length()));
            BigDecimal amount;
            try {
                amount = new BigDecimal(text.replaceAll("\\s+", ""));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid_positive_retry"));
                return;
            }
            user.setState("SELLER_INCREASE_NOTE:" + debtId + ":" + amount.toPlainString());
            userService.save(user);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.increase_note"));
            return;
        }
        if (state.startsWith("SELLER_INCREASE_NOTE:")) {
            String payload = state.substring("SELLER_INCREASE_NOTE:".length());
            int colonIdx = payload.indexOf(':');
            if (colonIdx < 0) {
                messenger.replyMarkdown(msg, loc.t(user, "err.internal_restart"));
                userService.clearState(user.getTelegramId());
                return;
            }
            Long debtId = Long.parseLong(payload.substring(0, colonIdx));
            BigDecimal amount = new BigDecimal(payload.substring(colonIdx + 1));
            String note = "-".equals(text.trim()) ? null : text.trim();
            try {
                Debt updated = debtService.increaseDebt(debtId, amount, note, user.getTelegramId());
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "seller.debt_increased", debtId, MessageFormatter.formatDebt(updated, lang)));

                // Audit log
                try {
                    auditService.log(user.getShop(), user.getTelegramId(), "DEBT_INCREASE",
                            debtId, MessageFormatter.money(amount));
                } catch (Exception ignored) {}

                // Mijozga xabar (klient tilida)
                try {
                    Lang clientLang = Lang.fromStored(updated.getClient().getLanguage());
                    String shopCurrency = updated.getShop() != null ? updated.getShop().getCurrency() : null;
                    messenger.execute(SendMessage.builder()
                            .chatId(updated.getClient().getTelegramId().toString())
                            .text(loc.t(clientLang, "notify.debt_increased",
                                    updated.getShop().getName(),
                                    MessageFormatter.money(amount, shopCurrency, clientLang),
                                    MessageFormatter.money(updated.getRemainingAmount(), shopCurrency, clientLang)))
                            .build());
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage());
                userService.clearState(user.getTelegramId());
            }
            return;
        }

        // SELLER — summa tahrirlash
        if (state.startsWith("SELLER_EDIT_AMOUNT:")) {
            Long debtId = Long.parseLong(state.substring("SELLER_EDIT_AMOUNT:".length()));
            BigDecimal newTotal;
            try {
                newTotal = new BigDecimal(text.replaceAll("\\s+", ""));
                if (newTotal.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.amount_invalid_positive"));
                return;
            }
            try {
                Debt updated = debtService.updateAmount(debtId, newTotal);
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "seller.amount_updated", MessageFormatter.formatDebt(updated, lang)));

                // Audit log
                try {
                    auditService.log(user.getShop(), user.getTelegramId(), "DEBT_EDIT_AMOUNT",
                            debtId, MessageFormatter.money(newTotal));
                } catch (Exception ignored) {}

                // Mijozga xabar (klient tilida)
                try {
                    Lang clientLang = Lang.fromStored(updated.getClient().getLanguage());
                    String shopCurrency = updated.getShop() != null ? updated.getShop().getCurrency() : null;
                    messenger.execute(SendMessage.builder()
                            .chatId(updated.getClient().getTelegramId().toString())
                            .text(loc.t(clientLang, "notify.amount_updated",
                                    updated.getShop().getName(),
                                    updated.getId(),
                                    MessageFormatter.money(updated.getRemainingAmount(), shopCurrency, clientLang)))
                            .build());
                } catch (Exception ignored) {}
            } catch (IllegalArgumentException e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage() + "\nQayta kiriting:");
            }
            return;
        }

        // SELLER — izoh tahrirlash
        if (state.startsWith("SELLER_EDIT_DESC:")) {
            Long debtId = Long.parseLong(state.substring("SELLER_EDIT_DESC:".length()));
            String description = "-".equals(text.trim()) ? null : text.trim();
            try {
                debtService.updateDescription(debtId, description);
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "seller.desc_updated"));

                // Audit log
                try {
                    auditService.log(user.getShop(), user.getTelegramId(), "DEBT_EDIT_DESC",
                            debtId, description != null ? description : "—");
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage());
                userService.clearState(user.getTelegramId());
            }
            return;
        }

        // SELLER — muddat tahrirlash
        if (state.startsWith("SELLER_EDIT_DUE:")) {
            Long debtId = Long.parseLong(state.substring("SELLER_EDIT_DUE:".length()));
            LocalDate dueDate = null;
            if (!"-".equals(text.trim())) {
                try {
                    dueDate = LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } catch (Exception e) {
                    messenger.replyMarkdown(msg, loc.t(user, "err.date_invalid_edit"));
                    return;
                }
            }
            try {
                debtService.updateDueDate(debtId, dueDate);
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "seller.due_updated"));

                // Audit log
                try {
                    auditService.log(user.getShop(), user.getTelegramId(), "DEBT_EDIT_DUE",
                            debtId, dueDate != null ? dueDate.toString() : "muddatsiz");
                } catch (Exception ignored) {}
            } catch (Exception e) {
                messenger.replyMarkdown(msg, "❌ " + e.getMessage());
                userService.clearState(user.getTelegramId());
            }
            return;
        }

        // SELLER — qidirish
        if (state.equals("SELLER_SEARCH")) {
            String query = text.trim();
            userService.clearState(user.getTelegramId());
            Shop shop = user.getShop();
            if (shop == null) {
                messenger.replyMarkdown(msg, loc.t(user, "err.no_shop_assigned"));
                return;
            }
            List<Debt> allDebts = debtService.findByShop(shop);
            String lowerQuery = query.toLowerCase();
            List<Debt> found = allDebts.stream()
                    .filter(d -> {
                        BotUser client = d.getClient();
                        String name = client.getFullName() != null ? client.getFullName().toLowerCase() : "";
                        String phone = client.getPhoneNumber() != null ? client.getPhoneNumber().toLowerCase() : "";
                        return name.contains(lowerQuery) || phone.contains(lowerQuery);
                    })
                    .toList();
            if (found.isEmpty()) {
                messenger.replyMarkdown(msg, loc.t(user, "seller.search_empty"));
                return;
            }
            int shown = 0;
            for (Debt d : found) {
                if (shown >= 15) break;
                messenger.execute(SendMessage.builder()
                        .chatId(msg.getChatId().toString())
                        .text(MessageFormatter.formatDebt(d, lang))
                        .parseMode("Markdown")
                        .replyMarkup(KeyboardFactory.debtActions(d.getId()))
                        .build());
                shown++;
            }
            return;
        }

        // SELLER — broadcast text handler
        if (state.equals("SELLER_BCAST_TEXT")) {
            Shop shop = user.getShop();
            if (shop == null) {
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "err.no_shop_assigned"));
                return;
            }
            List<BotUser> recipients = new ArrayList<>();
            for (var m : membershipService.acceptedMembers(shop)) {
                recipients.add(m.getClient());
            }
            userService.clearState(user.getTelegramId());
            int sent = messenger.broadcast(recipients, loc.t(user, "seller.broadcast_header", shop.getName()), text);
            try {
                auditService.log(shop, user.getTelegramId(), "BROADCAST", null, sent + " ta");
            } catch (Exception ignored) {}
            messenger.menu(user, msg.getChatId(), loc.t(user, "seller.broadcast_done", sent));
            return;
        }

        // SELLER — mahsulot miqdori kiritish (product-based debt)
        if (state.startsWith("SELLER_ADD_DEBT_QTY:")) {
            String[] parts = state.substring("SELLER_ADD_DEBT_QTY:".length()).split(":");
            Long clientId = Long.parseLong(parts[0]);
            Long productId = Long.parseLong(parts[1]);
            BigDecimal qty;
            try {
                qty = new BigDecimal(text.replaceAll("\\s+", "").replace(",", "."));
                if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                messenger.replyMarkdown(msg, loc.t(user, "err.qty_invalid"));
                return;
            }
            var prodOpt = productService.findById(productId);
            if (prodOpt.isEmpty()) {
                userService.clearState(user.getTelegramId());
                messenger.menu(user, msg.getChatId(), loc.t(user, "err.product_not_found"));
                return;
            }
            var product = prodOpt.get();
            BigDecimal amount = product.getPrice().multiply(qty);
            String qtyStr = qty.stripTrailingZeros().toPlainString();
            String desc = product.getName() + " × " + qtyStr
                    + (product.getUnit() != null && !product.getUnit().isBlank() ? " " + product.getUnit() : "");
            user.setState("SELLER_ADD_DEBT_DUE:" + clientId + ":" + amount.toPlainString() + ":" + desc);
            userService.save(user);
            messenger.replyMarkdown(msg, loc.t(user, "prompt.qty_calculated", MessageFormatter.money(amount, lang)));
            return;
        }

        // Holat SELLER_ bilan boshlanadi, lekin hech bir bosqichga mos kelmadi —
        // qotib qolmaslik uchun state'ni tozalab, asosiy menyuga qaytaramiz.
        userService.clearState(user.getTelegramId());
        messenger.menu(user, msg.getChatId(), loc.t(user, "common.cancelled"));
    }

    private void finishDebtCreate(BotUser seller, Message msg, Long clientId,
                                  BigDecimal amount, String description, LocalDate dueDate) {
        BotUser client = userService.findById(clientId).orElseThrow();
        Lang sellerLang = Lang.fromStored(seller.getLanguage());
        Debt debt = debtService.createDebt(seller.getShop(), client, seller, amount, description, dueDate);
        userService.clearState(seller.getTelegramId());
        messenger.menu(seller, msg.getChatId(), loc.t(seller, "seller.debt_created", MessageFormatter.formatDebt(debt, sellerLang)));
        try {
            auditService.log(seller.getShop(), seller.getTelegramId(), "DEBT_CREATE",
                    debt.getId(), MessageFormatter.money(amount));
        } catch (Exception ignored) {}
        try {
            // Mijozga chek — klient tilida.
            Lang clientLang = Lang.fromStored(client.getLanguage());
            messenger.execute(SendMessage.builder()
                    .chatId(client.getTelegramId().toString())
                    .text(loc.t(clientLang, "notify.new_debt", MessageFormatter.formatDebtReceipt(debt, clientLang)))
                    .parseMode("Markdown")
                    .build());
        } catch (Exception ignored) {}
    }
}
