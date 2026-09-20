package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewActionRepository extends JpaRepository<ReviewAction, UUID> {
    Optional<ReviewAction> findByTransactionId(UUID transactionId);
}
