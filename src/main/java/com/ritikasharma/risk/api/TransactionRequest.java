package com.ritikasharma.risk.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransactionRequest(
        @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotBlank @Size(max = 64) String payerAccountId,
        @NotBlank @Size(max = 200) String payerName,
        @NotBlank @Size(min = 2, max = 2) @Pattern(regexp = "[A-Za-z]{2}") String payerCountry,
        @NotBlank @Size(max = 200) String payeeName,
        @NotBlank @Size(min = 2, max = 2) @Pattern(regexp = "[A-Za-z]{2}") String payeeCountry
) {}
