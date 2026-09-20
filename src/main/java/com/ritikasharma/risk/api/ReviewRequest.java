package com.ritikasharma.risk.api;

import com.ritikasharma.risk.domain.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String comment
) {}
