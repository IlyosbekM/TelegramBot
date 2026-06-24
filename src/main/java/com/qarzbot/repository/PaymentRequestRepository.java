package com.qarzbot.repository;

import com.qarzbot.entity.PaymentRequest;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    @Query("SELECT r FROM PaymentRequest r JOIN FETCH r.debt d JOIN FETCH d.client JOIN FETCH d.shop WHERE r.id = :id")
    Optional<PaymentRequest> findByIdFetched(@Param("id") Long id);

    @Query("SELECT r FROM PaymentRequest r JOIN FETCH r.debt d JOIN FETCH d.client JOIN FETCH d.shop " +
           "WHERE d.shop = :shop AND r.status = :status")
    List<PaymentRequest> findByShopAndStatusFetched(@Param("shop") Shop shop,
                                                     @Param("status") PaymentRequest.PaymentRequestStatus status);
}
