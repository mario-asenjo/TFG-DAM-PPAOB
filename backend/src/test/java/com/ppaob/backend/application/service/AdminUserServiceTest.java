package com.ppaob.backend.application.service;

import com.ppaob.backend.application.port.out.UserAccountRepositoryPort;
import com.ppaob.backend.domain.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    @Test
    void replaceRolesBlocksRemovingLastEnabledAdmin() {
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        AuditService audit = mock(AuditService.class);
        AdminUserService service = new AdminUserService(users, audit);

        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserAccount current = new UserAccount(targetId, "admin@ppaob.local", "hash", true, Set.of("ADMIN"));

        when(users.findById(targetId)).thenReturn(Optional.of(current));
        when(users.countEnabledAdmins()).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.replaceRoles(targetId, Set.of("ANALYST"), actorId));
    }

    @Test
    void setEnabledBlocksDisablingLastEnabledAdmin() {
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        AuditService audit = mock(AuditService.class);
        AdminUserService service = new AdminUserService(users, audit);

        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserAccount current = new UserAccount(targetId, "admin@ppaob.local", "hash", true, Set.of("ADMIN"));

        when(users.findById(targetId)).thenReturn(Optional.of(current));
        when(users.countEnabledAdmins()).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.setEnabled(targetId, false, actorId));
    }

    @Test
    void setEnabledAllowsSafeToggleAndAudits() {
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        AuditService audit = mock(AuditService.class);
        AdminUserService service = new AdminUserService(users, audit);

        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserAccount current = new UserAccount(targetId, "analyst@ppaob.local", "hash", true, Set.of("ANALYST"));
        UserAccount updated = new UserAccount(targetId, "analyst@ppaob.local", "hash", false, Set.of("ANALYST"));

        when(users.findById(targetId)).thenReturn(Optional.of(current));
        when(users.setEnabled(targetId, false)).thenReturn(Optional.of(updated));
        service.setEnabled(targetId, false, actorId);
    }
}
