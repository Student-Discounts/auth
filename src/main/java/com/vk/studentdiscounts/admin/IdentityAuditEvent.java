package com.vk.studentdiscounts.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись журнала аудита. Только добавляется: изменение и удаление запрещены триггером в БД.
 */
@Entity
@Immutable
@Table(name = "identity_audit_events")
public class IdentityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject_user_id", nullable = false)
    private UUID subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IdentityAuditAction action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdentityAuditEvent() {
    }

    static IdentityAuditEvent of(UUID subjectUserId, IdentityAuditAction action, UUID actorId, String reason,
                                 String payload, String correlationId, Instant now) {
        IdentityAuditEvent event = new IdentityAuditEvent();
        event.subjectUserId = subjectUserId;
        event.action = action;
        event.actorId = actorId;
        event.reason = reason;
        event.payload = payload;
        event.correlationId = correlationId;
        event.createdAt = now;
        return event;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubjectUserId() {
        return subjectUserId;
    }

    public IdentityAuditAction getAction() {
        return action;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
