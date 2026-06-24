package com.qarzbot.repository;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Payment;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByDebt(Debt debt);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.debt.shop = :shop AND p.paidAt >= :from")
    BigDecimal sumCollectedSince(@Param("shop") Shop shop, @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.debt.shop = :shop AND p.paidAt >= :from")
    long countCollectedSince(@Param("shop") Shop shop, @Param("from") LocalDateTime from);
}
