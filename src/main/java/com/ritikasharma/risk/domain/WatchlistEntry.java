package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "watchlist_entries")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchlistEntry extends AuditedEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "list_source", nullable = false)
    private String listSource;

    @Column(name = "source_reference", nullable = false)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private WatchlistEntityType entityType;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** See {@link com.ritikasharma.risk.common.NameNormalizer}. */
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "country")
    private String country;

    @Column(name = "reason")
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active;
}
