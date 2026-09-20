package com.ritikasharma.risk.api;

import com.ritikasharma.risk.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {
        return ResponseEntity.accepted()
                .body(service.submit(idempotencyKey, request, merchantId(authentication)));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id, Authentication authentication) {
        return service.get(id, merchantId(authentication));
    }

    private String merchantId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String merchantId = jwt.getClaimAsString("merchant_id");
            if (merchantId != null && !merchantId.isBlank()) return merchantId;
        }
        throw new IllegalStateException("Merchant claim is missing");
    }
}
