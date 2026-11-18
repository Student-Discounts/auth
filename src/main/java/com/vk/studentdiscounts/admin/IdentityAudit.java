package com.vk.studentdiscounts.admin;

import com.vk.studentdiscounts.config.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Пишет журнал аудита. Запись выполняется только внутри транзакции изменения,
 * чтобы изменение и запись о нём сохранялись или откатывались вместе.
 */
@Component
public class IdentityAudit {

    private final IdentityAuditEventRepository events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdentityAudit(IdentityAuditEventRepository events, ObjectMapper mapper, Clock clock) {
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param actorId {@code null}, если действие выполнила система
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID subjectUserId, IdentityAuditAction action, UUID actorId, String reason,
                       Map<String, ?> payload) {
        events.save(IdentityAuditEvent.of(subjectUserId, action, actorId, reason, mapper.writeValueAsString(payload),
                MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant()));
    }
}
