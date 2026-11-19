package com.vk.studentdiscounts.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @param actorId {@code null}, если действие выполнила система
 * @param payload подробности действия, например {@code {"role": "MODERATOR"}} или {@code {"revokedSessions": 2}}
 */
public record IdentityEventResponse(UUID id,
                                    IdentityAuditAction action,
                                    UUID actorId,
                                    String reason,
                                    Map<String, Object> payload,
                                    String correlationId,
                                    Instant createdAt) {

    static IdentityEventResponse from(IdentityAuditEvent event, Map<String, Object> payload) {
        return new IdentityEventResponse(event.getId(), event.getAction(), event.getActorId(), event.getReason(),
                payload, event.getCorrelationId(), event.getCreatedAt());
    }
}
