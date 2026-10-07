package dev.prathamesh.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.prathamesh.model.AccountModel;
import dev.prathamesh.model.LedgerEntryModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.repository.LedgerEntryRepository;
import dev.prathamesh.types.AccountBalanceResponse;
import dev.prathamesh.types.AccountType;
import dev.prathamesh.types.EntryDirection;
import dev.prathamesh.types.LedgerVerifyResponse;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private volatile Long systemAccountId;

    public LedgerService(AccountRepository accountRepository,
                         LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public Long systemAccountId() {
        if (systemAccountId == null) {
            systemAccountId = accountRepository.findFirstByType(AccountType.SYSTEM)
                    .orElseThrow(() -> new IllegalStateException("System account missing"))
                    .getId();
        }
        return systemAccountId;
    }

    /** Moves money. Must run inside an existing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Long paymentId, Long debitAccountId, Long creditAccountId, BigDecimal amount) {

        if (debitAccountId.equals(creditAccountId)) {
            throw new IllegalArgumentException("Sender and receiver must be different");
        }

        // Lock the lower id first so opposite transfers can't deadlock
        Long firstId = Math.min(debitAccountId, creditAccountId);
        Long secondId = Math.max(debitAccountId, creditAccountId);
        AccountModel first = lock(firstId);
        AccountModel second = lock(secondId);

        AccountModel debit = first.getId().equals(debitAccountId) ? first : second;
        AccountModel credit = (debit == first) ? second : first;

        if (debit.getType() == AccountType.USER && debit.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        debit.setBalance(debit.getBalance().subtract(amount));
        credit.setBalance(credit.getBalance().add(amount));
        accountRepository.save(debit);
        accountRepository.save(credit);

        ledgerEntryRepository.save(new LedgerEntryModel(paymentId, debitAccountId, EntryDirection.DEBIT, amount));
        ledgerEntryRepository.save(new LedgerEntryModel(paymentId, creditAccountId, EntryDirection.CREDIT, amount));
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse accountBalance(Long accountId) {
        AccountModel account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account " + accountId + " not found"));
        BigDecimal ledger = ledgerEntryRepository.netBalance(accountId);
        if (ledger == null) ledger = BigDecimal.ZERO;
        return new AccountBalanceResponse(
                accountId, account.getBalance(), ledger,
                account.getBalance().compareTo(ledger) == 0);
    }

    @Transactional(readOnly = true)
    public LedgerVerifyResponse verify() {
        BigDecimal total = ledgerEntryRepository.totalNet();
        if (total == null) total = BigDecimal.ZERO;

        Map<Long, BigDecimal> ledger = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.netBalancesByAccount()) {
            ledger.put((Long) row[0], (BigDecimal) row[1]);
        }

        List<Long> mismatched = new ArrayList<>();
        for (AccountModel a : accountRepository.findAll()) {
            BigDecimal l = ledger.getOrDefault(a.getId(), BigDecimal.ZERO);
            if (a.getBalance().compareTo(l) != 0) {
                mismatched.add(a.getId());
            }
        }
        return new LedgerVerifyResponse(total, total.signum() == 0, mismatched);
    }

    private AccountModel lock(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Account " + id + " not found"));
    }
}