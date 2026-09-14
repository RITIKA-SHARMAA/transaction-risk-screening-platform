package com.ritikasharma.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.Objects;
import java.util.UUID;

/**
 * Base for entities whose UUID is assigned by the application before persisting (so it can be used as an
 * event id or Kafka key up front). Implements {@link Persistable} so {@code save()} issues an INSERT
 * instead of a SELECT followed by a merge.
 */
@MappedSuperclass
public abstract class AssignedIdEntity extends AuditedEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean newEntity = true;

    protected AssignedIdEntity() {
    }

    protected AssignedIdEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
