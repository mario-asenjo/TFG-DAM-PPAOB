package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.AdminUserResponse;
import com.ppaob.backend.adapters.in.rest.dto.AdminUserEnabledUpdateRequest;
import com.ppaob.backend.adapters.in.rest.dto.AdminUserRolesUpdateRequest;
import com.ppaob.backend.application.service.AdminUserService;
import com.ppaob.backend.domain.model.UserAccount;
import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
/**
 * Exposes administrative operations to inspect users and manage account state.
 */
public class AdminUserController {

    private final AdminUserService adminUsers;

    /**
     * Creates a controller backed by the administrative user service.
     *
     * @param adminUsers service used to list users and update roles or enabled state
     */
    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    /**
     * Lists all user accounts visible to administrators.
     *
     * @return user summaries with identifier, email, enabled flag and roles
     */
    @GetMapping
    public List<AdminUserResponse> listUsers() {
        return adminUsers.listUsers().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replaces the full role set assigned to a target user.
     *
     * @param userId identifier of the user to update
     * @param request payload with the complete role set to persist
     * @param authentication authenticated principal used as the acting administrator
     * @return updated user projection after role replacement
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @PutMapping("/{userId}/roles")
    public AdminUserResponse updateRoles(@PathVariable UUID userId, @Valid @RequestBody AdminUserRolesUpdateRequest request, Authentication authentication) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        UserAccount updated = adminUsers.replaceRoles(userId, request.roles(), actor.userId());
        return toResponse(updated);
    }

    /**
     * Enables or disables a user account.
     *
     * @param userId identifier of the user to update
     * @param request payload carrying the target enabled state
     * @param authentication authenticated principal used as the acting administrator
     * @return updated user projection after state change
     * @throws ClassCastException if the security principal is not an {@link AuthenticatedUser}
     */
    @PatchMapping("/{userId}/enabled")
    public AdminUserResponse updateEnabled(@PathVariable UUID userId, @Valid @RequestBody AdminUserEnabledUpdateRequest request, Authentication authentication) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        UserAccount updated = adminUsers.setEnabled(userId, request.enabled(), actor.userId());
        return toResponse(updated);
    }

    private AdminUserResponse toResponse(UserAccount account) {
        return new AdminUserResponse(account.userId(), account.email(), account.enabled(), account.roles());
    }
}
