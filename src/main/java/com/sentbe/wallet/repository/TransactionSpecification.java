package com.sentbe.wallet.repository;

import com.sentbe.wallet.domain.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class TransactionSpecification {

    /**
     * 월렛 ID, 시작일, 종료일을 기준으로 하는 동적 쿼리 Specification
     *
     * @param walletId  월렛 ID (필수)
     * @param startDate 시작일 (선택)
     * @param endDate   종료일 (선택)
     * @return Specification<Transaction>
     */
    public static Specification<Transaction> withWalletAndDateRange(String walletId, Instant startDate,
            Instant endDate) {
        return (root, query, cb) -> {
            var predicate = cb.equal(root.get("walletId"), walletId);

            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("withdrawalDate"), startDate));
            }

            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("withdrawalDate"), endDate));
            }

            return predicate;
        };
    }
}
