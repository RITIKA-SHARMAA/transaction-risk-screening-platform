package com.ritikasharma.risk.api;

import java.time.Instant;
import java.util.List;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, List<String> roles) {
}
