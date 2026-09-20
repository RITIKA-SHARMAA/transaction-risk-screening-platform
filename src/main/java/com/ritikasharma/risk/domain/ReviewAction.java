package com.ritikasharma.risk.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "review_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewAction extends AssignedIdEntity {
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "reviewer_username", nullable = false, updatable = false)
    private String reviewerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false)
    private Decision decision;

    @Column(name = "comment", updatable = false)
    private String comment;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    public ReviewAction(UUID id, UUID transactionId, String reviewerUsername, Decision decision,
                        String comment, Instant reviewedAt) {
        super(id);
        this.transactionId = Objects.requireNonNull(transactionId);
        this.reviewerUsername = Objects.requireNonNull(reviewerUsername);
        this.decision = Objects.requireNonNull(decision);
        this.comment = comment;
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
    }
}
