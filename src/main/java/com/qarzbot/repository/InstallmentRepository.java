package com.qarzbot.repository;

import com.qarzbot.entity.Debt;
import com.qarzbot.entity.Installment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    List<Installment> findByDebtOrderBySeqNoAsc(Debt debt);

    void deleteByDebt(Debt debt);

    /** Fetch installment together with its debt (avoids LazyInitializationException). */
    @Query("SELECT i FROM Installment i JOIN FETCH i.debt WHERE i.id = :id")
    Optional<Installment> findByIdFetched(@Param("id") Long id);

    /** Fetch all installments for a debt (by debtId), with debt eager-loaded, ordered by seqNo. */
    @Query("SELECT i FROM Installment i JOIN FETCH i.debt d WHERE d.id = :debtId ORDER BY i.seqNo ASC")
    List<Installment> findByDebtIdFetched(@Param("debtId") Long debtId);
}
