package com.ritikasharma.risk.api;

import java.time.Instant;
import java.util.List;

public record CurrentUserResponse(String username, List<String> roles, String merchantId, Instant tokenExpiresAt) {
}
