package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RiskSignalRepository extends JpaRepository<RiskSignal, UUID> {

    Optional<RiskSignal> findByTransactionId(UUID transactionId);
}
