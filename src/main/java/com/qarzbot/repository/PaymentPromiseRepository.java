package com.qarzbot.repository;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.PaymentPromise;
import com.qarzbot.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaymentPromiseRepository extends JpaRepository<PaymentPromise, Long> {

    boolean existsByDebtAndStatus(Debt debt, PaymentPromise.PromiseStatus status);

    /**
     * Berilgan do'kon uchun berilgan holatdagi (odatda OPEN) va'dalar, eng yaqin
     * va'da sanasi bo'yicha tartiblangan. Enum'ni JPQL ichida to'g'ridan-to'g'ri
     * (com.qarzbot.entity.PaymentPromise$PromiseStatus) yozish noqulay bo'lgani
     * uchun status parametr sifatida uzatiladi.
     */
    @Query("SELECT p FROM PaymentPromise p JOIN FETCH p.debt d JOIN FETCH d.shop " +
           "JOIN FETCH p.client JOIN FETCH d.client " +
           "WHERE d.shop = :shop AND p.status = :status ORDER BY p.promiseDate ASC")
    List<PaymentPromise> findOpenByShopFetched(@Param("shop") Shop shop,
                                                @Param("status") PaymentPromise.PromiseStatus status);

    /** Berilgan holatdagi va aynan berilgan sanadagi va'dalar (kunlik eslatma uchun). */
    @Query("SELECT p FROM PaymentPromise p JOIN FETCH p.debt d JOIN FETCH d.shop " +
           "JOIN FETCH p.client JOIN FETCH d.client " +
           "WHERE p.status = :status AND p.promiseDate = :date")
    List<PaymentPromise> findByStatusAndPromiseDateFetched(@Param("status") PaymentPromise.PromiseStatus status,
                                                            @Param("date") LocalDate date);

    /** Berilgan holatdagi va berilgan sanadan oldingi (muddati o'tgan) va'dalar (baholash uchun). */
    @Query("SELECT p FROM PaymentPromise p JOIN FETCH p.debt d JOIN FETCH d.shop " +
           "JOIN FETCH p.client JOIN FETCH d.client " +
           "WHERE p.status = :status AND p.promiseDate < :date")
    List<PaymentPromise> findByStatusAndPromiseDateBeforeFetched(@Param("status") PaymentPromise.PromiseStatus status,
                                                                  @Param("date") LocalDate date);
}
