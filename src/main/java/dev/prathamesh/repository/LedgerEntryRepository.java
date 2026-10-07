package dev.prathamesh.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.prathamesh.model.LedgerEntryModel;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryModel, Long>{
	// credits minus debits for one account (null if it has no entries)
    @Query("""
        SELECT SUM(CASE WHEN e.direction = dev.prathamesh.types.EntryDirection.CREDIT
                        THEN e.amount ELSE -e.amount END)
        FROM LedgerEntryModel e
        WHERE e.accountId = :accountId
    """)
    BigDecimal netBalance(@Param("accountId") Long accountId);
    
 // credits minus debits across the whole ledger: must always be 0
    @Query("""
        SELECT SUM(CASE WHEN e.direction = dev.prathamesh.types.EntryDirection.CREDIT
                        THEN e.amount ELSE -e.amount END)
        FROM LedgerEntryModel e
    """)
    BigDecimal totalNet();
    
    @Query("""
            SELECT e.accountId,
                   SUM(CASE WHEN e.direction = dev.prathamesh.types.EntryDirection.CREDIT
                            THEN e.amount ELSE -e.amount END)
            FROM LedgerEntryModel e
            GROUP BY e.accountId
        """)
        List<Object[]> netBalancesByAccount();
    
}