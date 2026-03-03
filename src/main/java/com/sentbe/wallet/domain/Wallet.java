package com.sentbe.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @Column(name = "wallet_id", length = 32)
    private String walletId;

    @Column(nullable = false)
    private Long balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Wallet(String walletId, Long balance) {
        this.walletId = walletId;
        this.balance = balance != null ? balance : 0L;
    }

    /**
     * 출금 도메인 메서드
     * 잔액 검증 및 차감 로직을 내장함.
     * 비관적 락(SELECT FOR UPDATE) 환경에서 안전하게 잔액을 갱신함.
     *
     * @param amount 출금할 금액
     * @throws IllegalArgumentException 출금 금액이 유효하지 않거나 잔액이 부족할 경우
     */
    public void withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }
        if (this.balance < amount) {
            throw new IllegalStateException(
                    "Insufficient balance. Current: " + this.balance + ", Requested: " + amount);
        }
        this.balance -= amount;
    }
}
