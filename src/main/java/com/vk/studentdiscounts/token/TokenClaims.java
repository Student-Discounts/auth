package com.vk.studentdiscounts.token;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Нестандартные claims access-токена (контракт описан в docs/design/RBAC.md, раздел 5).
 */
public final class TokenClaims {

    public static final String ROLES = "roles";
    public static final String SESSION_ID = "sid";

    private TokenClaims() {
    }

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static UUID sessionId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(SESSION_ID));
    }
}
