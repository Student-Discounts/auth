package com.vk.studentdiscounts.user;

/**
 * Роли пользователя. Матрица прав описана в docs/design/RBAC.md.
 */
public enum Role {
    STUDENT,
    ORG_REPRESENTATIVE,
    MODERATOR,
    SENIOR_MODERATOR
}
