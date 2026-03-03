package com.sentbe.wallet.repository;

import com.sentbe.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    /**
     * 비관적 락(PESSIMISTIC_WRITE)을 사용하여 월렛 정보를 조회합니다.
     * 동시성 제어를 위해 SELECT FOR UPDATE 쿼리를 실행합니다.
     *
     * @param walletId 월렛 ID
     * @return 월렛 정보 (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.walletId = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") String walletId);
}
