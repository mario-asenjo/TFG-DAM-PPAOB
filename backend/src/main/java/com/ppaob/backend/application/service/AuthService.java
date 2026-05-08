package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.ClockPort;
import com.ppaob.backend.application.port.out.RefreshSessionRepositoryPort;
import com.ppaob.backend.application.port.out.UserAccountRepositoryPort;
import com.ppaob.backend.domain.model.UserAccount;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import com.ppaob.backend.infrastructure.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@Service
/**
 * Handles user authentication lifecycle and refresh-session management.
 *
 * <p>This service owns registration defaults, credential verification, refresh-token rotation,
 * logout revocation, and best-effort audit logging for auth-relevant actions.
 */
public class AuthService {

    private final UserAccountRepositoryPort repository;
    private final RefreshSessionRepositoryPort refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final ClockPort clock;
    private final long refreshTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

/**
 * Creates the authentication service.
 *
 * @param repository user repository for account lookup and creation
 * @param refreshSessions refresh-session repository for token lifecycle operations
 * @param passwordEncoder password hasher/verifier
 * @param jwtService JWT issuer for access tokens
 * @param auditService audit logger used in best-effort mode
 * @param clock time source used for expiry and revocation timestamps
 * @param refreshTtlDays refresh-session TTL in days
 */
    public AuthService(
            UserAccountRepositoryPort repository,
            RefreshSessionRepositoryPort refreshSessions,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            ClockPort clock,
            @Value("${security.refresh.ttl-days:14}") long refreshTtlDays
    ) {
        this.repository = repository;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.clock = clock;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
/**
 * Registers a new user account.
 *
 * <p>New accounts are created enabled with the default {@code VIEWER} role.
 *
 * <p>Side effects: inserts user account in persistence and writes best-effort REGISTER audit event.
 *
 * @param email unique account email
 * @param rawPassword clear-text password to hash before storage
 * @return created user account
 * @throws IllegalArgumentException when email is already registered
 */
    public UserAccount register(String email, String rawPassword) {
        if (repository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        try {
            UserAccount created = repository.create(email, passwordEncoder.encode(rawPassword), Set.of("VIEWER"));
            safeAudit("REGISTER", "SUCCESS", created.userId(), null, null,
                    auditService.details("email", created.email(), "roles", created.roles()));
            return created;
        } catch (DataIntegrityViolationException ex) {
            safeAudit("REGISTER", "FAIL", null, null, null,
                    auditService.details("email", email, "reason", "CONFLICT"));
            throw new IllegalArgumentException("Email already registered");
        }
    }

    @Transactional
/**
 * Authenticates user credentials and creates a new auth session.
 *
 * <p>Validation flow:
 * - user must exist,
 * - user must be enabled,
 * - password must match stored hash.
 *
 * <p>On success, emits an access JWT and creates a persisted refresh session.
 *
 * <p>Side effects: writes best-effort LOGIN audit event and inserts refresh-session row.
 *
 * @param email account email
 * @param rawPassword clear-text password to verify
 * @param sourceIp optional source IP stored with refresh session metadata
 * @param userAgent optional user-agent stored with refresh session metadata
 * @return access token plus raw refresh token
 * @throws SecurityException when credentials are invalid or user is disabled
 */
    public AuthSessionResult login(String email, String rawPassword, String sourceIp, String userAgent) {
        UserAccount user = repository.findByEmail(email).orElse(null);
        if (user == null) {
            safeAudit("LOGIN", "FAIL", null, null, null,
                    auditService.details("email", email, "reason", "USER_NOT_FOUND"));
            throw new SecurityException("Invalid credentials");
        }

        if (!user.enabled()) {
            safeAudit("LOGIN", "FAIL", user.userId(), null, null,
                    auditService.details("email", email, "reason", "USER_DISABLED"));
            throw new SecurityException("User disabled");
        }

        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            safeAudit("LOGIN", "FAIL", user.userId(), null, null,
                    auditService.details("email", email, "reason", "BAD_PASSWORD"));
            throw new SecurityException("Invalid credentials");
        }

        safeAudit("LOGIN", "SUCCESS", user.userId(), null, null,
                auditService.details("email", user.email(), "roles", user.roles()));

        IssuedRefreshToken refreshToken = issueRefreshSession(user.userId(), sourceIp, userAgent);
        JwtService.JwtToken access = jwtService.issueToken(new AuthenticatedUser(user.userId(), user.email(), user.roles()));

        return new AuthSessionResult(access, refreshToken.rawToken());
    }

    @Transactional
/**
 * Rotates a valid refresh token and issues a new auth session.
 *
 * <p>Behavior:
 * - input token is SHA-256 hashed before lookup,
 * - current active session must exist and be unexpired,
 * - owning user must still exist and be enabled,
 * - a new refresh session is created,
 * - previous session is revoked with reason {@code ROTATED}.
 *
 * <p>Side effects: inserts new refresh-session row, revokes previous row, emits best-effort
 * REFRESH audit event.
 *
 * @param refreshToken raw refresh token
 * @param sourceIp optional source IP stored with new refresh session metadata
 * @param userAgent optional user-agent stored with new refresh session metadata
 * @return new access token plus rotated raw refresh token
 * @throws SecurityException when token is invalid, user is missing, or user is disabled
 */
    public AuthSessionResult refresh(String refreshToken, String sourceIp, String userAgent) {
        String tokenHash = hash(refreshToken);
        var activeSession = refreshSessions.findActiveByTokenHash(tokenHash, clock.now())
                .orElseThrow(() -> {
                    safeAudit("REFRESH", "FAIL", null, null, null, auditService.details("reason", "INVALID_REFRESH_TOKEN"));
                    return new SecurityException("Invalid refresh token");
                });

        UserAccount user = repository.findById(activeSession.userId())
                .orElseThrow(() -> new SecurityException("User not found"));

        if (!user.enabled()) {
            refreshSessions.revoke(activeSession.sessionId(), clock.now(), null, "USER_DISABLED");
            safeAudit("REFRESH", "FAIL", user.userId(), null, null, auditService.details("reason", "USER_DISABLED"));
            throw new SecurityException("User disabled");
        }

        IssuedRefreshToken newRefreshToken = issueRefreshSession(user.userId(), sourceIp, userAgent);
        refreshSessions.revoke(activeSession.sessionId(), clock.now(), newRefreshToken.sessionId(), "ROTATED");

        JwtService.JwtToken access = jwtService.issueToken(new AuthenticatedUser(user.userId(), user.email(), user.roles()));
        safeAudit("REFRESH", "SUCCESS", user.userId(), null, null,
                auditService.details("email", user.email()));

        return new AuthSessionResult(access, newRefreshToken.rawToken());
    }

    @Transactional
/**
 * Revokes an active refresh session for the provided token.
 *
 * <p>Blank tokens are ignored. Unknown/expired tokens are treated as no-op.
 *
 * <p>Side effects: may update one refresh-session row to revoked state and may emit a best-effort
 * LOGOUT audit event.
 *
 * @param refreshToken raw refresh token to revoke
 */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenHash = hash(refreshToken);
        var activeSession = refreshSessions.findActiveByTokenHash(tokenHash, clock.now());
        if (activeSession.isPresent()) {
            refreshSessions.revoke(activeSession.get().sessionId(), clock.now(), null, "LOGOUT");
            safeAudit("LOGOUT", "SUCCESS", activeSession.get().userId(), null, null, auditService.details());
        }
    }

/**
 * Returns an account by id for authenticated security flows.
 *
 * @param userId user identifier
 * @return user account
 * @throws SecurityException when user does not exist
 */
    public UserAccount getUserById(java.util.UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new SecurityException("User not found"));
    }

    private IssuedRefreshToken issueRefreshSession(java.util.UUID userId, String sourceIp, String userAgent) {
        byte[] raw = new byte[48];
        secureRandom.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant expiresAt = clock.now().plus(refreshTtlDays, ChronoUnit.DAYS);
        var session = refreshSessions.create(userId, hash(refreshToken), expiresAt, sourceIp, userAgent);
        return new IssuedRefreshToken(refreshToken, session.sessionId());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available");
        }
    }

    private void safeAudit(String action, String result, java.util.UUID userId, java.util.UUID analysisId, java.util.UUID binaryId, java.util.Map<String, Object> details) {
        try {
            auditService.log(action, result, userId, analysisId, binaryId, details);
        } catch (RuntimeException ignored) {
        }
    }

/**
 * Result DTO returned by login/refresh operations.
 *
 * @param accessToken issued JWT access token payload and expiry metadata
 * @param refreshToken raw refresh token that must be stored by the client
 */
    public record AuthSessionResult(JwtService.JwtToken accessToken, String refreshToken) {
        /**
         * Returns issued access token payload.
         *
         * @return access token
         */
        @Override
        public JwtService.JwtToken accessToken() {
            return accessToken;
        }
    }

    private record IssuedRefreshToken(String rawToken, java.util.UUID sessionId) {
    }
}
