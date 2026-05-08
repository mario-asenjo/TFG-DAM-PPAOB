package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.port.out.AnalysisRepositoryPort;
import com.ppaob.backend.domain.model.AnalysisRecord;
import com.ppaob.backend.domain.model.AnalysisResultRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link AnalysisRepositoryPort}.
 *
 * <p>Queries enforce requester visibility at SQL level: non-admin users can only
 * access their own analyses and related results.</p>
 */
@Repository
public class JdbcAnalysisRepository implements AnalysisRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for all persistence operations
     */
    public JdbcAnalysisRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Creates a pending analysis request for a binary uploaded by the requester.
     *
     * <p>The insert is conditional: no row is created when {@code binaryId} is not
     * linked to {@code requestedBy} in {@code binary_uploads}.</p>
     *
     * @param binaryId binary to analyze
     * @param requestedBy requester user id
     * @param profile analysis profile name
     * @return created analysis record with binary/user metadata, or empty when
     *         requester is not associated with the binary
     */
    public Optional<AnalysisRecord> create(UUID binaryId, UUID requestedBy, String profile) {
        return jdbc.query(
                """
                        WITH inserted AS (
                            INSERT INTO analyses(binary_id, requested_by, profile, status)
                            SELECT :binaryId, :requestedBy, :profile, 'PENDING'
                            WHERE EXISTS (
                                SELECT 1
                                FROM binary_uploads bu
                                WHERE bu.binary_id = :binaryId
                                  AND bu.user_id = :requestedBy
                            )
                            RETURNING analysis_id, binary_id, requested_by, profile, status,
                                      created_at, started_at, finished_at, error_summary
                        )
                        SELECT i.analysis_id, i.binary_id, b.original_name AS binary_original_name,
                               i.requested_by, u.email AS requested_by_email,
                               i.profile, i.status,
                               i.created_at, i.started_at, i.finished_at, i.error_summary
                        FROM inserted i
                        JOIN binaries b ON b.binary_id = i.binary_id
                        JOIN users u ON u.user_id = i.requested_by
                        """,
                new MapSqlParameterSource()
                        .addValue("binaryId", binaryId)
                        .addValue("requestedBy", requestedBy)
                        .addValue("profile", profile),
                (rs, rowNum) -> mapRowWithMetadata(rs)
        ).stream().findFirst();
    }

    @Override
    /**
     * Loads one analysis when visible to the requester.
     *
     * @param analysisId analysis identifier
     * @param requesterId user requesting the lookup
     * @param requesterIsAdmin whether requester has admin-level visibility
     * @return matching analysis with metadata, or empty when not found/not visible
     */
    public Optional<AnalysisRecord> findById(UUID analysisId, UUID requesterId, boolean requesterIsAdmin) {
        return jdbc.query(
                """
                        SELECT a.analysis_id, a.binary_id, b.original_name AS binary_original_name,
                               a.requested_by, u.email AS requested_by_email,
                               a.profile, a.status,
                               a.created_at, a.started_at, a.finished_at, a.error_summary
                        FROM analyses a
                        JOIN binaries b ON b.binary_id = a.binary_id
                        JOIN users u ON u.user_id = a.requested_by
                        WHERE a.analysis_id = :analysisId
                          AND (a.requested_by = :requesterId OR :requesterIsAdmin = TRUE)
                        """,
                new MapSqlParameterSource()
                        .addValue("analysisId", analysisId)
                        .addValue("requesterId", requesterId)
                        .addValue("requesterIsAdmin", requesterIsAdmin),
                (rs, rowNum) -> mapRowWithMetadata(rs)
        ).stream().findFirst();
    }

    @Override
    /**
     * Loads stored analysis result JSON when the analysis is visible.
     *
     * @param analysisId analysis identifier
     * @param requesterId user requesting the result
     * @param requesterIsAdmin whether requester can read any analysis
     * @return stored result record, or empty when absent/not visible
     */
    public Optional<AnalysisResultRecord> findResultByAnalysisId(UUID analysisId, UUID requesterId, boolean requesterIsAdmin) {
        return jdbc.query(
                """
                        SELECT ar.analysis_id, ar.schema_version, ar.results_json::text AS results_json, ar.stored_at
                        FROM analysis_results ar
                        JOIN analyses a ON a.analysis_id = ar.analysis_id
                        WHERE ar.analysis_id = :analysisId
                          AND (a.requested_by = :requesterId OR :requesterIsAdmin = TRUE)
                        """,
                new MapSqlParameterSource()
                        .addValue("analysisId", analysisId)
                        .addValue("requesterId", requesterId)
                        .addValue("requesterIsAdmin", requesterIsAdmin),
                (rs, rowNum) -> new AnalysisResultRecord(
                        rs.getObject("analysis_id", UUID.class),
                        rs.getInt("schema_version"),
                        rs.getString("results_json"),
                        rs.getObject("stored_at", OffsetDateTime.class).toInstant()
                )
        ).stream().findFirst();
    }

    @Override
    /**
     * Lists analyses visible to a requester, optionally filtered by binary id.
     *
     * @param requesterId user requesting the list
     * @param requesterIsAdmin whether requester can read all users' analyses
     * @param binaryId optional binary filter; {@code null} means any binary
     * @param limit maximum number of rows returned
     * @return analyses ordered by most recent creation time
     */
    public List<AnalysisRecord> list(UUID requesterId, boolean requesterIsAdmin, UUID binaryId, int limit) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT a.analysis_id, a.binary_id, b.original_name AS binary_original_name,
                               a.requested_by, u.email AS requested_by_email,
                               a.profile, a.status,
                               a.created_at, a.started_at, a.finished_at, a.error_summary
                        FROM analyses a
                        JOIN binaries b ON b.binary_id = a.binary_id
                        JOIN users u ON u.user_id = a.requested_by
                        WHERE (a.requested_by = :requesterId OR :requesterIsAdmin = TRUE)
                        """
        );

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requesterId", requesterId)
                .addValue("requesterIsAdmin", requesterIsAdmin)
                .addValue("limit", limit);

        if (binaryId != null) {
            sql.append(" AND a.binary_id = :binaryId");
            params.addValue("binaryId", binaryId);
        }

        sql.append(" ORDER BY a.created_at DESC LIMIT :limit");

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> mapRowWithMetadata(rs));
    }

    private AnalysisRecord mapRowWithMetadata(ResultSet rs) throws SQLException {
        return new AnalysisRecord(
                rs.getObject("analysis_id", UUID.class),
                rs.getObject("binary_id", UUID.class),
                rs.getString("binary_original_name"),
                rs.getObject("requested_by", UUID.class),
                rs.getString("requested_by_email"),
                rs.getString("profile"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("started_at", OffsetDateTime.class) == null ? null : rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                rs.getObject("finished_at", OffsetDateTime.class) == null ? null : rs.getObject("finished_at", OffsetDateTime.class).toInstant(),
                rs.getString("error_summary")
        );
    }
}
