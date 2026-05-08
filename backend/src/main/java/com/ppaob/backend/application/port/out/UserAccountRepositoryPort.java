package com.ppaob.backend.application.port.out;

import com.ppaob.backend.domain.model.UserAccount;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound persistence port for user accounts and role assignments.
 *
 * <p>This port defines account lookup and account-management operations used by
 * authentication and administration use cases. Implementations manage user-role
 * relationships and return normalized domain models for application logic.</p>
 */
public interface UserAccountRepositoryPort {
    /**
     * Finds a user account by email.
     *
     * @param email user email address
     * @return matching account, or empty when no account exists for the email
     */
    Optional<UserAccount> findByEmail(String email);

    /**
     * Finds a user account by identifier.
     *
     * @param userId user identifier
     * @return matching account, or empty when user does not exist
     */
    Optional<UserAccount> findById(UUID userId);

    /**
     * Lists all user accounts.
     *
     * @return all accounts ordered by implementation-defined criteria
     */
    List<UserAccount> listAll();

    /**
     * Creates a new user account with roles.
     *
     * <p>Side effects include persistence writes for the account and role links.</p>
     *
     * @param email user email address
     * @param passwordHash password hash
     * @param roles role names to assign
     * @return created account
     */
    UserAccount create(String email, String passwordHash, Set<String> roles);

    /**
     * Replaces role assignments for a user.
     *
     * @param userId user identifier
     * @param roles replacement role names
     * @return updated account, or empty when the user does not exist
     */
    Optional<UserAccount> replaceRoles(UUID userId, Set<String> roles);

    /**
     * Sets whether a user account is enabled.
     *
     * @param userId user identifier
     * @param enabled target enabled state
     * @return updated account, or empty when the user does not exist
     */
    Optional<UserAccount> setEnabled(UUID userId, boolean enabled);

    /**
     * Counts enabled accounts that currently have administrator role.
     *
     * @return number of enabled administrator accounts
     */
    int countEnabledAdmins();
}
