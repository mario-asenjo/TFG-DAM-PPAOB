package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.AuthResponse;
import com.ppaob.backend.adapters.in.rest.dto.LoginRequest;
import com.ppaob.backend.adapters.in.rest.dto.RegisterRequest;
import com.ppaob.backend.application.service.AuthService;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * Exposes registration, login, refresh, logout and identity endpoints.
 */
public class AuthController {

    private final AuthService authService;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final String refreshCookiePath;
    private final long refreshTtlDays;

    /**
     * Creates a controller configured for refresh-token cookie management.
     *
     * @param authService authentication service for account and token operations
     * @param refreshCookieName cookie name used to persist refresh tokens
     * @param refreshCookieSecure whether refresh cookie is marked secure
     * @param refreshCookieSameSite SameSite attribute used by refresh cookie
     * @param refreshCookiePath URL path bound to refresh cookie
     * @param refreshTtlDays refresh token lifetime in days
     */
    public AuthController(
            AuthService authService,
            @Value("${security.refresh.cookie-name:ppaob_refresh}") String refreshCookieName,
            @Value("${security.refresh.cookie-secure:false}") boolean refreshCookieSecure,
            @Value("${security.refresh.cookie-same-site:Lax}") String refreshCookieSameSite,
            @Value("${security.refresh.cookie-path:/api/v1/auth}") String refreshCookiePath,
            @Value("${security.refresh.ttl-days:14}") long refreshTtlDays
    ) {
        this.authService = authService;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieSameSite = refreshCookieSameSite;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshTtlDays = refreshTtlDays;
    }

    /**
     * Registers a new user account.
     *
     * @param request registration payload with email and password
     * @return map containing created user identifier, email and roles
     */
    @PostMapping("/auth/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        var created = authService.register(request.email(), request.password());
        return Map.of(
                "userId", created.userId(),
                "email", created.email(),
                "roles", created.roles()
        );
    }

    /**
     * Authenticates a user and issues access and refresh tokens.
     *
     * @param request credentials payload
     * @param httpRequest raw servlet request used for client metadata extraction
     * @return access token payload and a `Set-Cookie` header with refresh token
     * @throws SecurityException when credentials are rejected by the service layer
     */
    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var session = authService.login(request.email(), request.password(), getClientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        var token = session.accessToken();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(session.refreshToken(), Duration.ofDays(refreshTtlDays)).toString())
                .body(new AuthResponse(token.token(), token.expiresAt(), token.email(), token.roles()));
    }

    /**
     * Exchanges a valid refresh token cookie for a new session.
     *
     * @param httpRequest raw servlet request used to read refresh cookie and client metadata
     * @return refreshed access token payload and rotated refresh cookie
     * @throws SecurityException when refresh cookie is missing or token is invalid
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest httpRequest) {
        String refreshToken = extractRefreshCookie(httpRequest);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SecurityException("Missing refresh token");
        }

        var session = authService.refresh(refreshToken, getClientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        var token = session.accessToken();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(session.refreshToken(), Duration.ofDays(refreshTtlDays)).toString())
                .body(new AuthResponse(token.token(), token.expiresAt(), token.email(), token.roles()));
    }

    /**
     * Invalidates current refresh token and clears the cookie on client side.
     *
     * @param httpRequest raw servlet request used to read refresh cookie
     * @return confirmation payload and a clearing `Set-Cookie` header
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpRequest) {
        String refreshToken = extractRefreshCookie(httpRequest);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie("", Duration.ZERO).toString())
                .body(Map.of("status", "logged_out"));
    }

    /**
     * Returns the authenticated account profile.
     *
     * @param authentication authenticated principal from the security context
     * @return account identifier, email, roles and enabled state
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        var account = authService.getUserById(user.userId());
        return Map.of(
                "userId", account.userId(),
                "email", account.email(),
                "roles", account.roles(),
                "enabled", account.enabled()
        );
    }

    private ResponseCookie buildRefreshCookie(String tokenValue, Duration maxAge) {
        return ResponseCookie.from(refreshCookieName, tokenValue)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(maxAge)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
