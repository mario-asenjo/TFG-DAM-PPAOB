package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.port.out.BinaryRepositoryPort;
import com.ppaob.backend.domain.model.BinaryRecord;
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
 * JDBC implementation of {@link BinaryRepositoryPort}.
 *
 * <p>The repository persists binary metadata and ownership links in separate
 * tables so multiple users can reference the same binary content.</p>
 */
@Repository
public class JdbcBinaryRepository implements BinaryRepositoryPort {

    private static final String SELECT_BASE = """
            SELECT b.binary_id,
                   b.original_name,
                   b.sha256,
                   b.format,
                   b.size_bytes,
                   b.uploaded_at,
                   b.uploaded_by,
                   b.object_id,
                   so.bucket,
                   so.object_key
            FROM binaries b
            JOIN stored_objects so ON so.object_id = b.object_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for binary/object/link operations
     */
    public JdbcBinaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Finds a binary by its SHA-256 digest.
     *
     * @param sha256 binary digest in hexadecimal form
     * @return matching binary record, or empty when unknown
     */
    public Optional<BinaryRecord> findBySha256(String sha256) {
        String sql = SELECT_BASE + " WHERE b.sha256 = :sha256";
        var params = new MapSqlParameterSource("sha256", sha256);
        return jdbc.query(sql, params, (rs, rowNum) -> mapRow(rs)).stream().findFirst();
    }

    @Override
    /**
     * Loads a binary when visible to the requester.
     *
     * @param binaryId binary identifier
     * @param uploadedBy requester user id
     * @param uploaderIsAdmin whether requester can bypass uploader ownership
     * @return binary metadata for the latest uploader-link timestamp, or empty
     *         when not found/not visible
     */
    public Optional<BinaryRecord> findByIdForUploader(UUID binaryId, UUID uploadedBy, boolean uploaderIsAdmin) {
        String sql = """
                SELECT b.binary_id,
                       b.original_name,
                       b.sha256,
                       b.format,
                       b.size_bytes,
                       bu.uploaded_at,
                       bu.user_id AS uploaded_by,
                       b.object_id,
                       so.bucket,
                       so.object_key
                FROM binaries b
                JOIN binary_uploads bu ON bu.binary_id = b.binary_id
                JOIN stored_objects so ON so.object_id = b.object_id
                WHERE b.binary_id = :binaryId
                  AND (bu.user_id = :uploadedBy OR :uploaderIsAdmin = TRUE)
                ORDER BY bu.uploaded_at DESC
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("binaryId", binaryId)
                .addValue("uploadedBy", uploadedBy)
                .addValue("uploaderIsAdmin", uploaderIsAdmin);
        return jdbc.query(sql, params, (rs, rowNum) -> mapRow(rs)).stream().findFirst();
    }

    @Override
    /**
     * Creates a binary metadata row and links it to its uploader.
     *
     * <p>Side effects: inserts into {@code stored_objects}, inserts into
     * {@code binaries}, and creates an ownership link in {@code binary_uploads}.</p>
     *
     * @param originalName original uploaded filename
     * @param sha256 content SHA-256 digest
     * @param format detected/declared binary format
     * @param sizeBytes file size in bytes
     * @param uploadedBy uploader user id
     * @param bucket bucket where bytes are stored
     * @param objectKey storage key where bytes are stored
     * @return created binary record with storage metadata
     */
    public BinaryRecord create(String originalName, String sha256, String format, long sizeBytes, UUID uploadedBy, String bucket, String objectKey) {
        UUID objectId = jdbc.queryForObject(
                """
                        INSERT INTO stored_objects(provider, bucket, object_key, checksum_sha256, size_bytes)
                        VALUES ('S3', :bucket, :objectKey, :sha256, :sizeBytes)
                        RETURNING object_id
                        """,
                new MapSqlParameterSource()
                        .addValue("bucket", bucket)
                        .addValue("objectKey", objectKey)
                        .addValue("sha256", sha256)
                        .addValue("sizeBytes", sizeBytes),
                UUID.class
        );

        UUID binaryId = jdbc.queryForObject(
                """
                        INSERT INTO binaries(original_name, sha256, format, size_bytes, uploaded_by, object_id)
                        VALUES (:originalName, :sha256, :format, :sizeBytes, :uploadedBy, :objectId)
                        RETURNING binary_id
                        """,
                new MapSqlParameterSource()
                        .addValue("originalName", originalName)
                        .addValue("sha256", sha256)
                        .addValue("format", format)
                        .addValue("sizeBytes", sizeBytes)
                        .addValue("uploadedBy", uploadedBy)
                        .addValue("objectId", objectId),
                UUID.class
        );

        linkToUploader(binaryId, uploadedBy, "NEW_UPLOAD");

        String sql = SELECT_BASE + " WHERE b.binary_id = :binaryId";
        var params = new MapSqlParameterSource("binaryId", binaryId);
        return jdbc.queryForObject(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    @Override
    /**
     * Links an existing binary to an uploader source.
     *
     * <p>Side effects: writes into {@code binary_uploads}; duplicate
     * {@code (binary_id, user_id)} pairs are ignored via {@code ON CONFLICT DO NOTHING}.</p>
     *
     * @param binaryId binary identifier
     * @param uploadedBy uploader user id
     * @param source origin label for the link
     */
    public void linkToUploader(UUID binaryId, UUID uploadedBy, String source) {
        jdbc.update(
                """
                        INSERT INTO binary_uploads(binary_id, user_id, source)
                        VALUES (:binaryId, :uploadedBy, :source)
                        ON CONFLICT (binary_id, user_id) DO NOTHING
                        """,
                new MapSqlParameterSource()
                        .addValue("binaryId", binaryId)
                        .addValue("uploadedBy", uploadedBy)
                        .addValue("source", source)
        );
    }

    @Override
    /**
     * Lists binaries linked to one uploader.
     *
     * @param uploadedBy uploader user id
     * @return binaries ordered by uploader-link timestamp descending
     */
    public List<BinaryRecord> listByUploader(UUID uploadedBy) {
        String sql = """
                SELECT b.binary_id,
                       b.original_name,
                       b.sha256,
                       b.format,
                       b.size_bytes,
                       bu.uploaded_at,
                       bu.user_id AS uploaded_by,
                       b.object_id,
                       so.bucket,
                       so.object_key
                FROM binary_uploads bu
                JOIN binaries b ON b.binary_id = bu.binary_id
                JOIN stored_objects so ON so.object_id = b.object_id
                WHERE bu.user_id = :uploadedBy
                ORDER BY bu.uploaded_at DESC
                """;
        var params = new MapSqlParameterSource("uploadedBy", uploadedBy);
        return jdbc.query(sql, params, (rs, rowNum) -> mapRow(rs));
    }

    private BinaryRecord mapRow(ResultSet rs) throws SQLException {
        return new BinaryRecord(
                rs.getObject("binary_id", UUID.class),
                rs.getString("original_name"),
                rs.getString("sha256"),
                rs.getString("format"),
                rs.getLong("size_bytes"),
                rs.getObject("uploaded_at", OffsetDateTime.class).toInstant(),
                rs.getObject("uploaded_by", UUID.class),
                rs.getObject("object_id", UUID.class),
                rs.getString("bucket"),
                rs.getString("object_key")
        );
    }
}
