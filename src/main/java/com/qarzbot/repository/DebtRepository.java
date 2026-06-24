package com.qarzbot.repository;

import com.qarzbot.entity.BotUser;
import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop LEFT JOIN FETCH d.seller WHERE d.id = :id")
    Optional<Debt> findByIdFetched(@Param("id") Long id);


    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE d.client = :client")
    List<Debt> findByClient(@Param("client") BotUser client);

    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE d.client = :client AND d.status = :status")
    List<Debt> findByClientAndStatus(@Param("client") BotUser client, @Param("status") Debt.DebtStatus status);

    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE d.shop = :shop")
    List<Debt> findByShop(@Param("shop") Shop shop);

    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE d.shop = :shop AND d.status = :status")
    List<Debt> findByShopAndStatus(@Param("shop") Shop shop, @Param("status") Debt.DebtStatus status);

    @Query("SELECT COALESCE(SUM(d.totalAmount - d.paidAmount), 0) FROM Debt d WHERE d.shop = :shop AND d.status = 'ACTIVE'")
    BigDecimal totalActiveDebtByShop(@Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(d.totalAmount - d.paidAmount), 0) FROM Debt d WHERE d.client = :client AND d.status = 'ACTIVE'")
    BigDecimal totalActiveDebtByClient(@Param("client") BotUser client);

    @Query("SELECT d FROM Debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE d.status = 'ACTIVE' AND d.dueDate <= :date")
    List<Debt> findOverdueOrDueSoon(@Param("date") LocalDate date);

    @Query("SELECT d FROM Debt d WHERE d.status = 'ACTIVE' AND d.dueDate IS NOT NULL AND d.dueDate < :date")
    List<Debt> findActiveOverdue(@Param("date") LocalDate date);

    @Query("SELECT COUNT(d) FROM Debt d WHERE d.shop = :shop AND d.createdAt >= :from")
    long countNewDebtsSince(@Param("shop") Shop shop, @Param("from") LocalDateTime from);

    @Query("SELECT COALESCE(SUM(d.totalAmount), 0) FROM Debt d WHERE d.shop = :shop AND d.createdAt >= :from")
    BigDecimal sumNewDebtsSince(@Param("shop") Shop shop, @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(d) FROM Debt d WHERE d.shop = :shop AND d.status = 'ACTIVE'")
    long countActiveByShop(@Param("shop") Shop shop);

    @Query("SELECT COUNT(d) FROM Debt d WHERE d.shop = :shop AND d.status = 'ACTIVE' AND d.dueDate IS NOT NULL AND d.dueDate < :today")
    long countOverdueByShop(@Param("shop") Shop shop, @Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(d.totalAmount - d.paidAmount), 0) FROM Debt d WHERE d.shop = :shop AND d.status = 'ACTIVE' AND d.dueDate IS NOT NULL AND d.dueDate < :today")
    BigDecimal sumOverdueByShop(@Param("shop") Shop shop, @Param("today") LocalDate today);
}
