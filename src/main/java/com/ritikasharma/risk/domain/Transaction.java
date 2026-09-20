package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends AssignedIdEntity {

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private String merchantId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "payer_account_id", nullable = false, updatable = false)
    private String payerAccountId;

    @Column(name = "payer_name", nullable = false, updatable = false)
    private String payerName;

    @Column(name = "payer_country", nullable = false, updatable = false)
    private String payerCountry;

    @Column(name = "payee_name", nullable = false, updatable = false)
    private String payeeName;

    @Column(name = "payee_country", nullable = false, updatable = false)
    private String payeeCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Transaction(UUID id, String merchantId, BigDecimal amount, String currency,
                       String payerAccountId, String payerName, String payerCountry,
                       String payeeName, String payeeCountry, String correlationId) {
        super(id);
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.payerAccountId = Objects.requireNonNull(payerAccountId, "payerAccountId");
        this.payerName = Objects.requireNonNull(payerName, "payerName");
        this.payerCountry = Objects.requireNonNull(payerCountry, "payerCountry");
        this.payeeName = Objects.requireNonNull(payeeName, "payeeName");
        this.payeeCountry = Objects.requireNonNull(payeeCountry, "payeeCountry");
        this.correlationId = correlationId;
        this.status = TransactionStatus.PENDING;
    }

    public void applyDecision(Decision decision) {
        requirePending();
        this.status = TransactionStatus.of(decision);
    }

    public void applyReviewDecision(Decision decision) {
        if (status != TransactionStatus.IN_REVIEW) {
            throw new IllegalStateException("Transaction " + getId() + " is not awaiting review");
        }
        if (decision != Decision.APPROVE && decision != Decision.BLOCK) {
            throw new IllegalArgumentException("A review must end in APPROVE or BLOCK");
        }
        this.status = TransactionStatus.of(decision);
    }

    public void markFailed() {
        requirePending();
        this.status = TransactionStatus.FAILED;
    }

    private void requirePending() {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Transaction " + getId() + " is already " + status);
        }
    }
}
