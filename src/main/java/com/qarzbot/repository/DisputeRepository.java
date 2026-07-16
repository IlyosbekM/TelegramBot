package com.qarzbot.repository;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Dispute;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    boolean existsByDebtAndStatus(Debt debt, Dispute.DisputeStatus status);

    @Query("SELECT d FROM Dispute d JOIN FETCH d.debt dd JOIN FETCH d.client JOIN FETCH dd.shop " +
           "WHERE dd.shop = :shop AND d.status = :status ORDER BY d.createdAt ASC")
    List<Dispute> findOpenByShopFetched(@Param("shop") Shop shop, @Param("status") Dispute.DisputeStatus status);

    @Query("SELECT d FROM Dispute d JOIN FETCH d.debt dd JOIN FETCH dd.shop JOIN FETCH dd.client JOIN FETCH d.client " +
           "WHERE d.id = :id")
    Optional<Dispute> findByIdFetched(@Param("id") Long id);
}
