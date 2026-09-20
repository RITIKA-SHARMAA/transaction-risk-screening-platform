package com.ritikasharma.risk.security;

/** Custom claim names in issued access tokens (the subject is the username). */
public final class JwtClaims {

    public static final String ROLES = "roles";
    public static final String MERCHANT_ID = "merchant_id";

    private JwtClaims() {
    }
}
