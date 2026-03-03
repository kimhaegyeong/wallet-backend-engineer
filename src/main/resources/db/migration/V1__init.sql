CREATE TABLE wallet (
    wallet_id VARCHAR(32) PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    wallet_id VARCHAR(32) NOT NULL,
    withdrawal_amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_snapshot JSON,
    withdrawal_date TIMESTAMP(6) NOT NULL,
    INDEX idx_wallet_date (wallet_id, withdrawal_date),
    CONSTRAINT fk_transaction_wallet_id FOREIGN KEY (wallet_id) REFERENCES wallet (wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
