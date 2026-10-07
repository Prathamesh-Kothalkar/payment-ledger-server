package dev.prathamesh.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import dev.prathamesh.types.EntryDirection;
import jakarta.persistence.*;

@Entity
@Immutable
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_ledger_account", columnList = "account_id"),
        @Index(name = "idx_ledger_payment", columnList = "payment_id")
    }
)
public class LedgerEntryModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 6)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryModel() {
        // Required by JPA
    }

    public LedgerEntryModel(Long paymentId, Long accountId, EntryDirection direction, BigDecimal amount) {
        this.paymentId = paymentId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public Long getAccountId() { return accountId; }
    public EntryDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}