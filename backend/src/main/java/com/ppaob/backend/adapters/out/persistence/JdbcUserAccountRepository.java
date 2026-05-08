package com.ppaob.backend.adapters.out.persistence;

import com.ppaob.backend.application.port.out.UserAccountRepositoryPort;
import com.ppaob.backend.domain.model.UserAccount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC implementation of {@link UserAccountRepositoryPort}.
 *
 * <p>Role data is aggregated from join tables and represented as a set in the
 * returned domain model.</p>
 */
@Repository
public class JdbcUserAccountRepository implements UserAccountRepositoryPort {

    private static final String BASE_QUERY = """
            SELECT u.user_id,
                   u.email,
                   u.password_hash,
                   u.enabled,
                   COALESCE(array_agg(r.name) FILTER (WHERE r.name IS NOT NULL), '{}') AS roles
            FROM users u
            LEFT JOIN user_roles ur ON ur.user_id = u.user_id
            LEFT JOIN roles r ON r.role_id = ur.role_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the repository with a named-parameter JDBC template.
     *
     * @param jdbc JDBC template used for user and role persistence
     */
    public JdbcUserAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    /**
     * Finds a user account by email, case-insensitively.
     *
     * @param email user email to search
     * @return account with aggregated roles, or empty when unknown
     */
    public Optional<UserAccount> findByEmail(String email) {
        var sql = BASE_QUERY + " WHERE lower(u.email) = lower(:email) GROUP BY u.user_id, u.email, u.password_hash, u.enabled";
        var params = new MapSqlParameterSource("email", email);
        return jdbc.query(sql, params, (rs, rowNum) -> toUser(rs)).stream().findFirst();
    }

    @Override
    /**
     * Finds a user account by id.
     *
     * @param userId user identifier
     * @return account with aggregated roles, or empty when unknown
     */
    public Optional<UserAccount> findById(UUID userId) {
        var sql = BASE_QUERY + " WHERE u.user_id = :userId GROUP BY u.user_id, u.email, u.password_hash, u.enabled";
        var params = new MapSqlParameterSource("userId", userId);
        return jdbc.query(sql, params, (rs, rowNum) -> toUser(rs)).stream().findFirst();
    }

    @Override
    /**
     * Lists all user accounts.
     *
     * @return users ordered by lowercase email ascending
     */
    public List<UserAccount> listAll() {
        var sql = BASE_QUERY + " GROUP BY u.user_id, u.email, u.password_hash, u.enabled ORDER BY lower(u.email) ASC";
        return jdbc.query(sql, (rs, rowNum) -> toUser(rs));
    }

    @Override
    /**
     * Creates a new enabled user and assigns requested roles.
     *
     * <p>Side effects: inserts one row into {@code users} and attempts role links
     * in {@code user_roles}; unknown role names produce no link rows.</p>
     *
     * @param email user email
     * @param passwordHash password hash value
     * @param roles role names to attach
     * @return newly created account reloaded from persistence
     */
    public UserAccount create(String email, String passwordHash, Set<String> roles) {
        var userId = jdbc.queryForObject(
                """
                        INSERT INTO users(email, password_hash, enabled)
                        VALUES (:email, :passwordHash, TRUE)
                        RETURNING user_id
                        """,
                new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("passwordHash", passwordHash),
                UUID.class
        );

        for (String role : roles) {
            jdbc.update(
                    """
                            INSERT INTO user_roles(user_id, role_id)
                            SELECT :userId, role_id FROM roles WHERE name = :role
                            ON CONFLICT DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("role", role)
            );
        }

        return Objects.requireNonNull(findById(userId).orElse(null));
    }

    @Override
    /**
     * Replaces the role set for a user.
     *
     * <p>Side effects: removes existing links from {@code user_roles}, then inserts
     * links for provided role names that exist in {@code roles}.</p>
     *
     * @param userId user identifier
     * @param roles replacement role names
     * @return updated account, or empty when user does not exist
     */
    public Optional<UserAccount> replaceRoles(UUID userId, Set<String> roles) {
        int deleted = jdbc.update(
                "DELETE FROM user_roles WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId)
        );

        if (deleted == 0 && findById(userId).isEmpty()) {
            return Optional.empty();
        }

        for (String role : roles) {
            jdbc.update(
                    """
                            INSERT INTO user_roles(user_id, role_id)
                            SELECT :userId, role_id FROM roles WHERE name = :role
                            ON CONFLICT DO NOTHING
                            """,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("role", role)
            );
        }

        return findById(userId);
    }

    @Override
    /**
     * Enables or disables a user account.
     *
     * <p>Side effects: updates {@code users.enabled} for the target row.</p>
     *
     * @param userId user identifier
     * @param enabled target enabled state
     * @return updated account, or empty when user does not exist
     */
    public Optional<UserAccount> setEnabled(UUID userId, boolean enabled) {
        int updated = jdbc.update(
                "UPDATE users SET enabled = :enabled WHERE user_id = :userId",
                new MapSqlParameterSource()
                        .addValue("enabled", enabled)
                        .addValue("userId", userId)
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(userId);
    }

    @Override
    /**
     * Counts enabled users that currently have the {@code ADMIN} role.
     *
     * @return number of enabled administrator accounts
     */
    public int countEnabledAdmins() {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM users u
                        JOIN user_roles ur ON ur.user_id = u.user_id
                        JOIN roles r ON r.role_id = ur.role_id
                        WHERE u.enabled = TRUE
                          AND r.name = 'ADMIN'
                        """,
                new MapSqlParameterSource(),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private UserAccount toUser(ResultSet rs) throws SQLException {
        Array rolesArray = rs.getArray("roles");
        Object[] values = rolesArray == null ? new Object[0] : (Object[]) rolesArray.getArray();
        Set<String> roles = Arrays.stream(values).map(String::valueOf).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        return new UserAccount(
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getBoolean("enabled"),
                roles
        );
    }
}
