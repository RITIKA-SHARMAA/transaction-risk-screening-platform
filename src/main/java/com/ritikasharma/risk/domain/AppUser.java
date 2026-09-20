package com.ritikasharma.risk.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * API user. Roles are an element collection of enum values (not an entity association). Written without
 * Lombok because of the collection mapping, and without {@code toString} so the hash never ends up in logs.
 */
@Entity
@Table(name = "app_users")
public class AppUser extends AssignedIdEntity {

    @Column(name = "username", nullable = false, updatable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected AppUser() {
    }

    public AppUser(UUID id, String username, String passwordHash, String merchantId, Set<Role> roles) {
        super(id);
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.merchantId = merchantId;
        this.enabled = true;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
