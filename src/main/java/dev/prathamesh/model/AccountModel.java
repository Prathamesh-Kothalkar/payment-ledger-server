
package dev.prathamesh.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "accounts",
    indexes = {
        @Index(
            name = "idx_accounts_user_id",
            columnList = "user_id",
            unique = true
        )
    }
)
public class AccountModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(
        name = "user_id",
        nullable = false,
        unique = true
    )
    private Long userId;

    @Column(
        name = "balance",
        nullable = false,
        precision = 19,
        scale = 4
    )
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    @Column(
        name = "version",
        nullable = false
    )
    private Long version;

    public AccountModel() {
        // Required by JPA
    }

    public AccountModel(Long userId) {
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

	public void setUserId(Long id2) {
		
		
	}
}

