package com.ppaob.backend.support;

import com.ppaob.backend.infrastructure.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;

public final class TestAuth {

    private TestAuth() {
    }

    public static UsernamePasswordAuthenticationToken token(UUID userId, String email, Set<String> roles) {
        var principal = new AuthenticatedUser(userId, email, roles);
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
