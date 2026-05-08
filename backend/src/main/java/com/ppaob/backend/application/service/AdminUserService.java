package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.UserAccountRepositoryPort;
import com.ppaob.backend.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
/**
 * Manages administrative user operations over role assignment and account enablement.
 *
 * <p>This service enforces role-domain rules (allowed role values and last-admin protection)
 * before persisting changes through {@link UserAccountRepositoryPort}. It also emits best-effort
 * audit entries for successful updates; audit failures are intentionally ignored so admin actions
 * are not blocked.
 */
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "ANALYST", "VIEWER");

    private final UserAccountRepositoryPort users;
    private final AuditService auditService;

/**
 * Creates the administrative user service.
 *
 * @param users repository used to load and mutate user accounts
 * @param auditService audit logger used for best-effort security traceability
 */
    public AdminUserService(UserAccountRepositoryPort users, AuditService auditService) {
        this.users = users;
        this.auditService = auditService;
    }

/**
 * Returns all user accounts visible to the administration workflow.
 *
 * @return ordered list returned by the user repository
 */
    public List<UserAccount> listUsers() {
        return users.listAll();
    }

/**
 * Replaces the complete role set of a target account.
 *
 * <p>Business rules enforced before persisting:
 * - role values are normalized to uppercase and blanks are discarded,
 * - at least one role must remain,
 * - only {@code ADMIN}, {@code ANALYST}, and {@code VIEWER} are accepted,
 * - the last enabled admin cannot lose the {@code ADMIN} role.
 *
 * <p>Side effects: updates the user_roles mapping in persistence and writes a best-effort audit
 * event ({@code USER_ROLE_UPDATE/SUCCESS}).
 *
 * @param targetUserId identifier of the account being modified
 * @param newRoles new complete role set to apply
 * @param actorUserId authenticated actor performing the change, used in audit data
 * @return updated user account state after role replacement
 * @throws IllegalArgumentException when roles are invalid, target user does not exist, or the
 *                                  change would remove {@code ADMIN} from the last enabled admin
 */
    public UserAccount replaceRoles(UUID targetUserId, Set<String> newRoles, UUID actorUserId) {
        Set<String> normalized = normalizeRoles(newRoles);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        if (!ALLOWED_ROLES.containsAll(normalized)) {
            throw new IllegalArgumentException("Invalid role value. Allowed: ADMIN, ANALYST, VIEWER");
        }

        UserAccount current = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean removesAdmin = current.roles().contains("ADMIN") && !normalized.contains("ADMIN");
        if (removesAdmin && current.enabled() && users.countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException("Cannot remove ADMIN from the last enabled admin user");
        }

        UserAccount updated = users.replaceRoles(targetUserId, normalized)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        safeAudit(
                "USER_ROLE_UPDATE",
                "SUCCESS",
                actorUserId,
                null,
                null,
                auditService.details(
                        "targetUserId", updated.userId(),
                        "targetEmail", updated.email(),
                        "roles", updated.roles()
                )
        );
        return updated;
    }

/**
 * Enables or disables a user account.
 *
 * <p>The operation refuses to disable the last enabled admin account.
 *
 * <p>Side effects: updates account status in persistence and writes a best-effort audit event
 * ({@code USER_ENABLED_UPDATE/SUCCESS}).
 *
 * @param targetUserId identifier of the account to update
 * @param enabled target enabled state
 * @param actorUserId authenticated actor performing the change, used in audit data
 * @return updated user account state after the enablement change
 * @throws IllegalArgumentException when the target user does not exist or disabling would violate
 *                                  last-admin protection
 */
    public UserAccount setEnabled(UUID targetUserId, boolean enabled, UUID actorUserId) {
        UserAccount current = users.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!enabled && current.enabled() && current.roles().contains("ADMIN") && users.countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException("Cannot disable the last enabled admin user");
        }

        UserAccount updated = users.setEnabled(targetUserId, enabled)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        safeAudit(
                "USER_ENABLED_UPDATE",
                "SUCCESS",
                actorUserId,
                null,
                null,
                auditService.details(
                        "targetUserId", updated.userId(),
                        "targetEmail", updated.email(),
                        "enabled", updated.enabled()
                )
        );
        return updated;
    }

    private Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                normalized.add(role.trim().toUpperCase());
            }
        }
        return normalized;
    }

    private void safeAudit(String action, String result, UUID userId, UUID analysisId, UUID binaryId, java.util.Map<String, Object> details) {
        try {
            auditService.log(action, result, userId, analysisId, binaryId, details);
        } catch (RuntimeException ignored) {
        }
    }
}
