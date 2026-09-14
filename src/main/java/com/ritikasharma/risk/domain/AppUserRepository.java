package com.ritikasharma.risk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Login lookup; {@code username} must already be lower case. Roles are loaded eagerly. */
    Optional<AppUser> findByUsername(String username);
}
