package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.port.out.ArtifactRepositoryPort;
import com.ppaob.backend.domain.model.ArtifactRecord;
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
 * JDBC implementation of {@link ArtifactRepositoryPort}.
 *
 * <p>Artifact visibility follows ownership of the parent analysis unless the
 * requester has admin privileges.</p>
 */
@Repository
public class JdbcArtifactRepository implements ArtifactRepositoryPort {

    private static final String SELECT_BASE = """
            SELECT ar.artifact_id,
                   ar.analysis_id,
                   b.original_name AS binary_original_name,
                   ar.type,
                   ar.created_at,
                   ar.object_id,
                   so.bucket,
                   so.object_key,
                   COALESCE(so.size_bytes, 0) AS size_bytes
            FROM artifacts ar
            JOIN stored_objects so ON so.object_id = ar.object_id
            JOIN analyses a ON a.analysis_id = ar.analysis_id
            JOIN binaries b ON b.binary_id = a.binary_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for artifact and object persistence
     */
    public JdbcArtifactRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Creates a stored object row and links it to a new artifact entry.
     *
     * <p>Side effects: inserts into {@code stored_objects} and {@code artifacts}.</p>
     *
     * @param analysisId parent analysis identifier
     * @param type artifact type label
     * @param bucket storage bucket containing artifact bytes
     * @param objectKey storage key containing artifact bytes
     * @param checksumSha256 SHA-256 checksum for integrity/audit metadata
     * @param sizeBytes object size in bytes
     * @return created artifact with joined storage and binary metadata
     */
    public ArtifactRecord create(UUID analysisId, String type, String bucket, String objectKey, String checksumSha256, long sizeBytes) {
        UUID objectId = jdbc.queryForObject(
                """
                        INSERT INTO stored_objects(provider, bucket, object_key, checksum_sha256, size_bytes)
                        VALUES ('S3', :bucket, :objectKey, :checksumSha256, :sizeBytes)
                        RETURNING object_id
                        """,
                new MapSqlParameterSource()
                        .addValue("bucket", bucket)
                        .addValue("objectKey", objectKey)
                        .addValue("checksumSha256", checksumSha256)
                        .addValue("sizeBytes", sizeBytes),
                UUID.class
        );

        UUID artifactId = jdbc.queryForObject(
                """
                        INSERT INTO artifacts(analysis_id, type, object_id)
                        VALUES (:analysisId, :type, :objectId)
                        RETURNING artifact_id
                        """,
                new MapSqlParameterSource()
                        .addValue("analysisId", analysisId)
                        .addValue("type", type)
                        .addValue("objectId", objectId),
                UUID.class
        );

        String sql = SELECT_BASE + " WHERE ar.artifact_id = :artifactId";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("artifactId", artifactId), (rs, rowNum) -> mapRow(rs));
    }

    @Override
    /**
     * Lists recent artifacts visible to a requester.
     *
     * @param requesterId requester user id
     * @param requesterIsAdmin whether requester can read artifacts from all users
     * @param limit maximum number of records
     * @return artifacts ordered by creation timestamp descending
     */
    public List<ArtifactRecord> listForRequester(UUID requesterId, boolean requesterIsAdmin, int limit) {
        return jdbc.query(
                SELECT_BASE + """
                         WHERE (a.requested_by = :requesterId OR :requesterIsAdmin = TRUE)
                         ORDER BY ar.created_at DESC
                         LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("requesterId", requesterId)
                        .addValue("requesterIsAdmin", requesterIsAdmin)
                        .addValue("limit", limit),
                (rs, rowNum) -> mapRow(rs)
        );
    }

    @Override
    /**
     * Loads one artifact when visible to the requester.
     *
     * @param artifactId artifact identifier
     * @param requesterId requester user id
     * @param requesterIsAdmin whether requester can bypass ownership filter
     * @return artifact record when found and visible, otherwise empty
     */
    public Optional<ArtifactRecord> findByIdForRequester(UUID artifactId, UUID requesterId, boolean requesterIsAdmin) {
        return jdbc.query(
                SELECT_BASE + """
                         WHERE ar.artifact_id = :artifactId
                           AND (a.requested_by = :requesterId OR :requesterIsAdmin = TRUE)
                        """,
                new MapSqlParameterSource()
                        .addValue("artifactId", artifactId)
                        .addValue("requesterId", requesterId)
                        .addValue("requesterIsAdmin", requesterIsAdmin),
                (rs, rowNum) -> mapRow(rs)
        ).stream().findFirst();
    }

    private ArtifactRecord mapRow(ResultSet rs) throws SQLException {
        return new ArtifactRecord(
                rs.getObject("artifact_id", UUID.class),
                rs.getObject("analysis_id", UUID.class),
                rs.getString("binary_original_name"),
                rs.getString("type"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("object_id", UUID.class),
                rs.getString("bucket"),
                rs.getString("object_key"),
                rs.getLong("size_bytes")
        );
    }
}
