package com.sentbe.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "transaction", indexes = {
        @Index(name = "idx_wallet_date", columnList = "wallet_id, withdrawal_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    @Pattern(regexp = "^TXN_[a-fA-F0-9]{32}$", message = "Invalid transaction ID format.")
    private String transactionId;

    @Column(name = "wallet_id", nullable = false, length = 32)
    private String walletId;

    @Column(name = "withdrawal_amount", nullable = false)
    private Long withdrawalAmount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", columnDefinition = "json")
    private String responseSnapshot;

    @Column(name = "withdrawal_date", nullable = false)
    private Instant withdrawalDate;

    @Builder
    public Transaction(String transactionId, String walletId, Long withdrawalAmount, Long balanceAfter,
            TransactionStatus status, String responseSnapshot, Instant withdrawalDate) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.withdrawalAmount = withdrawalAmount;
        this.balanceAfter = balanceAfter;
        this.status = status;
        this.responseSnapshot = responseSnapshot;
        this.withdrawalDate = withdrawalDate != null ? withdrawalDate : Instant.now();
    }
}
