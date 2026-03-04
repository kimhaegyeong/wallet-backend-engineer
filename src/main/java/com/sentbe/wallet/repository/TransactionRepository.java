package com.sentbe.wallet.repository;

import com.sentbe.wallet.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * transactionId로 거래 내역을 조회합니다. (멱등성 체크용)
     *
     * @param transactionId 클라이언트가 생성한 거래 고유 ID
     * @return 거래 내역 (Optional)
     */
    Optional<Transaction> findByTransactionId(String transactionId);

    /**
     * 특정 월렛의 모든 거래 내역을 조회합니다.
     *
     * @param walletId 월렛 ID
     * @return 거래 내역 리스트
     */
    java.util.List<Transaction> findByWalletId(String walletId);
}
