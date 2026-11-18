package com.vk.studentdiscounts.admin;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdentityAuditEventRepository extends JpaRepository<IdentityAuditEvent, UUID> {

    List<IdentityAuditEvent> findBySubjectUserIdOrderByCreatedAtDescIdDesc(UUID subjectUserId, Limit limit);
}
