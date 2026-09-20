package com.ritikasharma.risk.api;

import com.ritikasharma.risk.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReviewItemResponse> queue(@RequestParam(defaultValue = "50") int limit) {
        return service.queue(Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/{transactionId}")
    public ReviewItemResponse get(@PathVariable UUID transactionId) {
        return service.get(transactionId);
    }

    @PostMapping("/{transactionId}/decision")
    public ReviewItemResponse decide(@PathVariable UUID transactionId,
                                     @Valid @RequestBody ReviewRequest request,
                                     Authentication authentication) {
        return service.decide(transactionId, request, authentication.getName());
    }
}
