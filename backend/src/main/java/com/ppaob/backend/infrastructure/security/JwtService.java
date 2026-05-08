package com.ppaob.backend.infrastructure.security;

import com.ppaob.backend.application.port.out.ClockPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
/**
 * Issues and validates JWT access tokens used by the HTTP security layer.
 *
 * <p>Tokens are signed with the configured HMAC secret and include subject ({@code userId}),
 * {@code email} and {@code roles} claims. Expiration is computed from the injected clock to
 * keep time-dependent behavior deterministic in tests.
 */
public class JwtService {

    private final SecretKey key;
    private final long tokenTtlMinutes;
    private final ClockPort clock;

/**
 * Creates a JWT service with signing material and token lifetime configuration.
 *
 * @param secret HMAC secret from {@code security.jwt.secret}; must be long enough for the
 *               selected JJWT HMAC algorithm
 * @param tokenTtlMinutes token lifetime in minutes from issue time
 * @param clock application clock used to compute issued-at and expiration instants
 */
    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.ttl-minutes:15}") long tokenTtlMinutes,
            ClockPort clock
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.clock = clock;
    }

/**
 * Builds a signed access token for an authenticated principal.
 *
 * <p>Side effects: none beyond cryptographic token generation in memory.
 *
 * @param user authenticated principal data to embed in token claims
 * @return immutable token payload containing compact JWT string and mirrored claim metadata
 */
    public JwtToken issueToken(AuthenticatedUser user) {
        Instant now = clock.now();
        Instant expiry = now.plus(tokenTtlMinutes, ChronoUnit.MINUTES);

        String token = Jwts.builder()
                .subject(user.userId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("email", user.email())
                .claim("roles", user.roles())
                .signWith(key)
                .compact();

        return new JwtToken(token, expiry, user.email(), user.roles());
    }

/**
 * Parses and validates a compact JWT, then maps claims to an authenticated principal.
 *
 * @param token compact bearer token without the {@code Bearer } prefix
 * @return authenticated principal reconstructed from JWT subject/email/roles claims
 * @throws io.jsonwebtoken.JwtException when signature, format or expiration validation fails
 * @throws IllegalArgumentException when the subject claim is not a valid UUID
 */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        List<?> rawRoles = claims.get("roles", List.class);
        Set<String> roles = rawRoles == null ? Set.of() : rawRoles.stream().map(String::valueOf).collect(Collectors.toSet());

        return new AuthenticatedUser(UUID.fromString(userId), email, roles);
    }

/**
 * Immutable access-token payload returned to authentication clients.
 *
 * @param token compact signed JWT string
 * @param expiresAt token expiration timestamp in UTC
 * @param email user email claim echoed from authenticated principal
 * @param roles granted application roles echoed from authenticated principal
 */
    public record JwtToken(String token, Instant expiresAt, String email, Set<String> roles) {}
}
