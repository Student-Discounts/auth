package com.vk.studentdiscounts.authz;

/**
 * Разрешения из матрицы прав (docs/design/RBAC.md, раздел 4). Endpoint защищается разрешением, а не ролью:
 * {@code @PreAuthorize("hasAuthority('ACCOUNT_BLOCK')")}.
 */
public enum Permission {
    SUBMISSION_CREATE,
    SUBMISSION_READ_OWN,
    SUBMISSION_EDIT_OWN,
    SUBMISSION_CANCEL_OWN,
    MEDIA_UPLOAD,
    REPORT_CREATE,
    ORG_MEMBERSHIP_REQUEST,
    ORG_CHANGE_PROPOSE,

    MODERATION_QUEUE_READ,
    MODERATION_TAKE,
    MODERATION_DECIDE,
    MODERATION_ESCALATE,
    REPORT_REVIEW,
    MODERATION_AUDIT_READ,
    ORG_MEMBERSHIP_VERIFY,

    MODERATION_ESCALATED_DECIDE,
    ROLE_MODERATOR_MANAGE,
    ACCOUNT_BLOCK,
    SESSION_REVOKE_ANY,
    IDENTITY_AUDIT_READ,
    USER_READ_ANY
}
