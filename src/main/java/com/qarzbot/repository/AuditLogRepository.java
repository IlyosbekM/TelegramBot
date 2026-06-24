package com.qarzbot.repository;

import com.qarzbot.entity.AuditLog;
import com.qarzbot.entity.Shop;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.shop = :shop ORDER BY a.createdAt DESC")
    List<AuditLog> recentByShop(@Param("shop") Shop shop, Pageable pageable);
}

