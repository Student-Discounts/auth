package com.vk.studentdiscounts.session;

import java.time.Instant;
import java.util.UUID;

/**
 * @param current {@code true}, если это сессия, из которой выполнен запрос
 */
public record SessionResponse(UUID id,
                              String userAgent,
                              Instant createdAt,
                              Instant lastUsedAt,
                              Instant expiresAt,
                              boolean current) {

    static SessionResponse from(UserSession session, UUID currentSessionId) {
        return new SessionResponse(session.getId(), session.getUserAgent(), session.getCreatedAt(),
                session.getLastUsedAt(), session.getExpiresAt(), session.getId().equals(currentSessionId));
    }
}
