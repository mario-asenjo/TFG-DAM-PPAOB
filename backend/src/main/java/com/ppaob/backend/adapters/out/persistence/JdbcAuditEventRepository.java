package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.service.AuditEventFilter;
import com.ppaob.backend.application.port.out.AuditEventRepositoryPort;
import com.ppaob.backend.domain.model.AuditEventRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of {@link AuditEventRepositoryPort}.
 */
@Repository
public class JdbcAuditEventRepository implements AuditEventRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for audit event writes and queries
     */
    public JdbcAuditEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Appends a single audit event entry.
     *
     * <p>Side effects: writes one row into {@code audit_events}; when
     * {@code detailsJson} is {@code null}, an empty JSON object is stored.</p>
     *
     * @param action normalized action identifier
     * @param result outcome identifier
     * @param userId optional actor user id
     * @param analysisId optional related analysis id
     * @param binaryId optional related binary id
     * @param detailsJson JSON object serialized as text
     */
    public void append(String action, String result, UUID userId, UUID analysisId, UUID binaryId, String detailsJson) {
        jdbc.update(
                """
                        INSERT INTO audit_events(action, result, user_id, analysis_id, binary_id, details)
                        VALUES (:action, :result, :userId, :analysisId, :binaryId, CAST(:detailsJson AS JSONB))
                        """,
                new MapSqlParameterSource()
                        .addValue("action", action)
                        .addValue("result", result)
                        .addValue("userId", userId)
                        .addValue("analysisId", analysisId)
                        .addValue("binaryId", binaryId)
                        .addValue("detailsJson", detailsJson == null ? "{}" : detailsJson)
        );
    }

    @Override
    /**
     * Lists most recent audit events without additional filtering.
     *
     * @param limit maximum number of rows
     * @return recent events ordered by timestamp descending
     */
    public List<AuditEventRecord> listRecent(int limit) {
        return jdbc.query(
                """
                        SELECT ae.event_id, ae.ts, ae.action, ae.result,
                               ae.user_id, u.email AS user_email,
                               ae.analysis_id, ae.binary_id,
                               b.original_name AS binary_original_name,
                               ae.details::text AS details_json
                        FROM audit_events ae
                        LEFT JOIN users u ON u.user_id = ae.user_id
                        LEFT JOIN binaries b ON b.binary_id = ae.binary_id
                        ORDER BY ts DESC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> mapRow(rs)
        );
    }

    @Override
    /**
     * Lists audit events using dynamic predicates from a filter object.
     *
     * @param filter optional-by-field filter plus pagination controls
     * @return events matching provided predicates, ordered by timestamp descending
     */
    public List<AuditEventRecord> listByFilter(AuditEventFilter filter) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT ae.event_id, ae.ts, ae.action, ae.result,
                               ae.user_id, u.email AS user_email,
                               ae.analysis_id, ae.binary_id,
                               b.original_name AS binary_original_name,
                               ae.details::text AS details_json
                        FROM audit_events ae
                        LEFT JOIN users u ON u.user_id = ae.user_id
                        LEFT JOIN binaries b ON b.binary_id = ae.binary_id
                        """
        );
        List<String> predicates = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter.action() != null) {
            predicates.add("ae.action = :action");
            params.addValue("action", filter.action());
        }
        if (filter.result() != null) {
            predicates.add("ae.result = :result");
            params.addValue("result", filter.result());
        }
        if (filter.userId() != null) {
            predicates.add("ae.user_id = :userId");
            params.addValue("userId", filter.userId());
        }
        if (filter.analysisId() != null) {
            predicates.add("ae.analysis_id = :analysisId");
            params.addValue("analysisId", filter.analysisId());
        }
        if (filter.binaryId() != null) {
            predicates.add("ae.binary_id = :binaryId");
            params.addValue("binaryId", filter.binaryId());
        }
        if (filter.from() != null) {
            predicates.add("ae.ts >= :fromTs");
            params.addValue("fromTs", filter.from());
        }
        if (filter.to() != null) {
            predicates.add("ae.ts <= :toTs");
            params.addValue("toTs", filter.to());
        }

        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        sql.append(" ORDER BY ae.ts DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", filter.limit());
        params.addValue("offset", filter.offset());

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> mapRow(rs));
    }

    private AuditEventRecord mapRow(ResultSet rs) throws SQLException {
        return new AuditEventRecord(
                rs.getObject("event_id", UUID.class),
                rs.getObject("ts", OffsetDateTime.class).toInstant(),
                rs.getString("action"),
                rs.getString("result"),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_email"),
                rs.getObject("analysis_id", UUID.class),
                rs.getObject("binary_id", UUID.class),
                rs.getString("binary_original_name"),
                rs.getString("details_json")
        );
    }
}
