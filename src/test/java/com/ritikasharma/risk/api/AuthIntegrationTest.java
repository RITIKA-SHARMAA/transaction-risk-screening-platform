package com.ritikasharma.risk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ritikasharma.risk.IntegrationTest;
import com.ritikasharma.risk.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the dev users seeded by db/seed-dev (loaded by the test profile). */
@IntegrationTest
class AuthIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String REVIEWER_ONLY = "/api/v1/reviews/queue";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void successfulLoginIssuesBearerTokenWithRolesAndMerchant() throws Exception {
        JsonNode body = json(login("merchant.demo", "merchant-dev-password")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.accessTokenTtl().toSeconds()))
                .andExpect(jsonPath("$.roles[0]").value("MERCHANT")));

        String token = body.get("accessToken").asText();
        assertThat(token.split("\\.")).hasSize(3);

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("merchant.demo"))
                .andExpect(jsonPath("$.roles", contains("MERCHANT")))
                .andExpect(jsonPath("$.merchantId").value("merchant-demo"));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        login("  Reviewer.DEMO ", "reviewer-dev-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("REVIEWER"));
    }

    @Test
    void wrongPasswordIsUnauthorizedProblem() throws Exception {
        expectProblem(login("merchant.demo", "not-the-password"), 401)
                .andExpect(jsonPath("$.detail").value("Invalid username or password."))
                .andExpect(jsonPath("$.instance").value(LOGIN));
    }

    @Test
    void unknownUserGetsTheSameResponseAsWrongPassword() throws Exception {
        JsonNode unknown = json(expectProblem(login("no.such.user", "whatever-password"), 401));
        JsonNode wrongPassword = json(expectProblem(login("merchant.demo", "whatever-password"), 401));

        assertThat(unknown).isEqualTo(wrongPassword);
    }

    @Test
    void blankCredentialsAreBadRequestProblem() throws Exception {
        expectProblem(login("", ""), 400);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = mint(jwtEncoder, jwtProperties.issuer(), issuedAt, issuedAt.plus(Duration.ofHours(1)));

        expectInvalidToken(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)));
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        expectInvalidToken(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer not.a-valid.jwt")));
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        byte[] otherKey = (UUID.randomUUID() + "" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        Instant now = Instant.now();
        String forged = mint(foreignEncoder, jwtProperties.issuer(), now, now.plus(Duration.ofMinutes(5)));

        expectInvalidToken(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)));
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorized() throws Exception {
        Instant now = Instant.now();
        String token = mint(jwtEncoder, "someone-else", now, now.plus(Duration.ofMinutes(5)));

        expectInvalidToken(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void missingTokenOnProtectedRouteIsUnauthorized() throws Exception {
        expectProblem(mockMvc.perform(get(ME)), 401)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."));
    }

    @Test
    void merchantTokenIsForbiddenOnReviewerRoute() throws Exception {
        String merchantToken = accessToken("merchant.demo", "merchant-dev-password");

        expectProblem(mockMvc.perform(get(REVIEWER_ONLY).header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken)), 403)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("insufficient_scope")))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.instance").value(REVIEWER_ONLY));
    }

    @Test
    void reviewerTokenPassesAuthorizationOnReviewerRoute() throws Exception {
        String reviewerToken = accessToken("reviewer.demo", "reviewer-dev-password");

        // No review endpoints exist yet: getting past security means a 404, not a 401 or 403.
        mockMvc.perform(get(REVIEWER_ONLY).header(HttpHeaders.AUTHORIZATION, "Bearer " + reviewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthAndPrometheusStayPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))));
    }

    private String accessToken(String username, String password) throws Exception {
        return json(login(username, password).andExpect(status().isOk())).get("accessToken").asText();
    }

    private static ResultActions expectProblem(ResultActions result, int status) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").exists());
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        expectProblem(result, 401)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.detail").value("The bearer token is malformed, expired or has an invalid signature."));
    }

    private static String mint(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("merchant.demo")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("MERCHANT"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
