package com.ritikasharma.risk.security;

import com.ritikasharma.risk.domain.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Verifies credentials and issues an access token. Unknown users and wrong passwords both surface as
 * {@link org.springframework.security.authentication.BadCredentialsException}, and the DAO provider hashes a
 * dummy password for unknown users so response timing does not reveal which usernames exist.
 */
@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public LoginService(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                        JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        this.authenticationManager = new ProviderManager(provider);
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws AuthenticationException for bad credentials or a disabled account */
    public IssuedToken login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        AppUserPrincipal user = (AppUserPrincipal) authentication.getPrincipal();

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        List<String> roles = user.roles().stream().sorted(Comparator.naturalOrder()).map(Role::name).toList();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(JwtClaims.ROLES, roles);
        if (user.merchantId() != null) {
            claims.claim(JwtClaims.MERCHANT_ID, user.merchantId());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, issuedAt, expiresAt, user.roles());
    }
}
