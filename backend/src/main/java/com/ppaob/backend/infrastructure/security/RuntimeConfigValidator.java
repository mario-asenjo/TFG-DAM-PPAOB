package com.ppaob.backend.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RuntimeConfigValidator implements ApplicationRunner {

    private static final String DEFAULT_DB_PASSWORD = "ppaob_dev_password";
    private static final String DEFAULT_S3_SECRET = "minioadmin_dev_password";
    private static final String DEFAULT_JWT_SECRET = "change-this-in-production-change-this-in-production";

    private final String runtimeMode;
    private final String jwtSecret;
    private final String dbPassword;
    private final String s3Secret;
    private final boolean refreshCookieSecure;
    private final String corsAllowedOrigins;

    public RuntimeConfigValidator(
            @Value("${app.runtime.mode:dev}") String runtimeMode,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${storage.s3.secret-key}") String s3Secret,
            @Value("${security.refresh.cookie-secure:false}") boolean refreshCookieSecure,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String corsAllowedOrigins
    ) {
        this.runtimeMode = runtimeMode;
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.s3Secret = s3Secret;
        this.refreshCookieSecure = refreshCookieSecure;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void run(ApplicationArguments args) {
        String normalized = runtimeMode.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("prod") && !normalized.equals("prod-like")) {
            return;
        }

        ensure(jwtSecret.length() >= 32, "JWT_SECRET must be at least 32 characters in prod-like/prod mode.");
        ensure(!DEFAULT_JWT_SECRET.equals(jwtSecret), "JWT_SECRET cannot use the development default in prod-like/prod mode.");
        ensure(!DEFAULT_DB_PASSWORD.equals(dbPassword), "DB_PASSWORD cannot use the development default in prod-like/prod mode.");
        ensure(!DEFAULT_S3_SECRET.equals(s3Secret), "S3_SECRET_KEY cannot use the development default in prod-like/prod mode.");
        ensure(refreshCookieSecure, "REFRESH_COOKIE_SECURE must be true in prod-like/prod mode.");

        String origins = corsAllowedOrigins.toLowerCase(Locale.ROOT);
        ensure(!origins.contains("localhost") && !origins.contains("127.0.0.1"),
                "APP_CORS_ALLOWED_ORIGINS cannot contain localhost/127.0.0.1 in prod-like/prod mode.");
    }

    private void ensure(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
