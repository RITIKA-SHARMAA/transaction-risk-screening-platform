package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScreeningSignalRepository extends JpaRepository<ScreeningSignal, UUID> {

    Optional<ScreeningSignal> findByTransactionId(UUID transactionId);
}
