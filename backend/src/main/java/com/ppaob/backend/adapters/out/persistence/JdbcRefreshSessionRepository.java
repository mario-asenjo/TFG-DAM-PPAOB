package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.port.out.RefreshSessionRepositoryPort;
import com.ppaob.backend.domain.model.RefreshSessionRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link RefreshSessionRepositoryPort}.
 */
@Repository
public class JdbcRefreshSessionRepository implements RefreshSessionRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for refresh-session persistence
     */
    public JdbcRefreshSessionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Creates a refresh session row.
     *
     * <p>Side effects: inserts one row in {@code refresh_sessions} with nullable
     * client metadata fields if they are null.</p>
     *
     * @param userId owner user id
     * @param tokenHash hashed refresh token value
     * @param expiresAt absolute expiration instant
     * @param createdIp optional client IP captured at issuance
     * @param createdUserAgent optional user agent captured at issuance
     * @return created refresh-session record
     */
    public RefreshSessionRecord create(UUID userId, String tokenHash, Instant expiresAt, String createdIp, String createdUserAgent) {
        return jdbc.queryForObject(
                """
                        INSERT INTO refresh_sessions(user_id, token_hash, expires_at, created_ip, created_user_agent)
                        VALUES (CAST(:userId AS UUID),
                                CAST(:tokenHash AS CHAR(64)),
                                CAST(:expiresAt AS TIMESTAMPTZ),
                                CAST(:createdIp AS VARCHAR(64)),
                                CAST(:createdUserAgent AS TEXT))
                        RETURNING session_id, user_id, token_hash, expires_at, created_at, revoked_at, replaced_by, revoked_reason
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId, Types.OTHER)
                        .addValue("tokenHash", tokenHash, Types.CHAR)
                        .addValue("expiresAt", asOffsetDateTime(expiresAt), Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue("createdIp", createdIp, Types.VARCHAR)
                        .addValue("createdUserAgent", createdUserAgent, Types.VARCHAR),
                (rs, rowNum) -> mapRow(rs)
        );
    }

    @Override
    /**
     * Finds one active (not revoked and not expired) refresh session by token hash.
     *
     * @param tokenHash hashed refresh token value
     * @param now reference instant used for expiration filtering
     * @return active session, or empty when absent/revoked/expired
     */
    public Optional<RefreshSessionRecord> findActiveByTokenHash(String tokenHash, Instant now) {
        return jdbc.query(
                """
                        SELECT session_id, user_id, token_hash, expires_at, created_at, revoked_at, replaced_by, revoked_reason
                        FROM refresh_sessions
                        WHERE token_hash = CAST(:tokenHash AS CHAR(64))
                          AND revoked_at IS NULL
                          AND expires_at > CAST(:now AS TIMESTAMPTZ)
                        """,
                new MapSqlParameterSource()
                        .addValue("tokenHash", tokenHash, Types.CHAR)
                        .addValue("now", asOffsetDateTime(now), Types.TIMESTAMP_WITH_TIMEZONE),
                (rs, rowNum) -> mapRow(rs)
        ).stream().findFirst();
    }

    @Override
    /**
     * Revokes a refresh session if it has not been revoked yet.
     *
     * <p>Side effects: updates {@code revoked_at}, {@code replaced_by} and
     * {@code revoked_reason} only when {@code revoked_at IS NULL}.</p>
     *
     * @param sessionId session identifier
     * @param revokedAt revocation timestamp
     * @param replacedBy optional replacement session id
     * @param reason short revocation reason code
     */
    public void revoke(UUID sessionId, Instant revokedAt, UUID replacedBy, String reason) {
        jdbc.update(
                """
                        UPDATE refresh_sessions
                        SET revoked_at = CAST(:revokedAt AS TIMESTAMPTZ),
                            replaced_by = CAST(:replacedBy AS UUID),
                            revoked_reason = CAST(:reason AS VARCHAR(32))
                        WHERE session_id = CAST(:sessionId AS UUID)
                          AND revoked_at IS NULL
                        """,
                new MapSqlParameterSource()
                        .addValue("sessionId", sessionId, Types.OTHER)
                        .addValue("revokedAt", asOffsetDateTime(revokedAt), Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue("replacedBy", replacedBy, Types.OTHER)
                        .addValue("reason", reason, Types.VARCHAR)
        );
    }

    private RefreshSessionRecord mapRow(ResultSet rs) throws SQLException {
        return new RefreshSessionRecord(
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("revoked_at", OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("revoked_at", OffsetDateTime.class).toInstant(),
                rs.getObject("replaced_by", UUID.class),
                rs.getString("revoked_reason")
        );
    }

    private OffsetDateTime asOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
