package com.qarzbot.service;

import com.qarzbot.entity.AuditLog;
import com.qarzbot.entity.Shop;
import com.qarzbot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(Shop shop, Long actorTelegramId, String action, Long debtId, String details) {
        AuditLog entry = AuditLog.builder()
                .shop(shop)
                .actorTelegramId(actorTelegramId)
                .action(action)
                .debtId(debtId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
    }

    public List<AuditLog> recent(Shop shop, int limit) {
        return auditLogRepository.recentByShop(shop, PageRequest.of(0, limit));
    }
}
