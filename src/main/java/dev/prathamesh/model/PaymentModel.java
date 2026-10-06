
package dev.prathamesh.model;

import dev.prathamesh.types.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(
            name = "idx_payments_source_account",
            columnList = "source_account_id"
        ),
        @Index(
            name = "idx_payments_destination_account",
            columnList = "destination_account_id"
        ),
        @Index(
            name = "idx_payments_status",
            columnList = "status"
        )
    },
    uniqueConstraints = {
    		@UniqueConstraint(
    		            name = "uk_payment_idempotency_key",
    		            columnNames = "idempotency_key"
    		        )
    		    }
)
public class PaymentModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(
        name = "source_account_id",
        nullable = false
    )
    private Long sourceAccountId;

    @Column(
        name = "destination_account_id",
        nullable = false
    )
    private Long destinationAccountId;

    @Column(
        name = "amount",
        nullable = false,
        precision = 19,
        scale = 4
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private PaymentStatus status;

    @Column(
        name = "idempotency_key",
        nullable = false,
        unique = true,
        length = 100
    )
    private String idempotencyKey;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(
        name = "updated_at",
        nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version;

    public PaymentModel() {
        // Required by JPA
    }

    public PaymentModel(
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String idempotencyKey
    ) {
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
}

