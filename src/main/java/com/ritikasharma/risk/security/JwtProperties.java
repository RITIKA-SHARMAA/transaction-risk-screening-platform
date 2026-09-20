package com.ritikasharma.risk.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings. {@code secret} has no default anywhere in the application configuration: it must come
 * from the environment ({@code JWT_SECRET}), and startup fails when it is missing or too short.
 */
@Validated
@ConfigurationProperties("risk.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "risk.security.jwt.secret (JWT_SECRET) must be set and at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (accessTokenTtl != null && (accessTokenTtl.isNegative() || accessTokenTtl.isZero())) {
            throw new IllegalArgumentException("risk.security.jwt.access-token-ttl must be positive");
        }
    }

    /** Keeps the secret out of logs and actuator output. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + ", secret=****]";
    }
}
