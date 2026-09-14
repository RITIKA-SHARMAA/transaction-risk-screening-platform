package com.ritikasharma.risk.security;

import com.ritikasharma.risk.domain.Role;

import java.time.Instant;
import java.util.Set;

public record IssuedToken(String value, Instant issuedAt, Instant expiresAt, Set<Role> roles) {
}
