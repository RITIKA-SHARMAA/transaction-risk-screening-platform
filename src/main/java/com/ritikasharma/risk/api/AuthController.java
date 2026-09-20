package com.ritikasharma.risk.api;

import com.ritikasharma.risk.security.IssuedToken;
import com.ritikasharma.risk.security.JwtClaims;
import com.ritikasharma.risk.security.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        IssuedToken token = loginService.login(request.username(), request.password());
        TokenResponse body = new TokenResponse(
                token.value(),
                "Bearer",
                Duration.between(token.issuedAt(), token.expiresAt()).toSeconds(),
                token.expiresAt(),
                token.roles().stream().map(Enum::name).sorted().toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(JwtClaims.ROLES), List.of());
        return new CurrentUserResponse(jwt.getSubject(), roles, jwt.getClaimAsString(JwtClaims.MERCHANT_ID),
                jwt.getExpiresAt());
    }
}
