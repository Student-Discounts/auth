package com.vk.studentdiscounts.authz;

import com.vk.studentdiscounts.user.Role;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.vk.studentdiscounts.authz.Permission.*;

/**
 * Единственное место, где роли превращаются в разрешения. Права ролей суммируются,
 * а старший модератор получает все права модератора.
 */
public final class RolePermissions {

    private static final Set<Permission> STUDENT = EnumSet.of(
            SUBMISSION_CREATE, SUBMISSION_READ_OWN, SUBMISSION_EDIT_OWN, SUBMISSION_CANCEL_OWN,
            MEDIA_UPLOAD, REPORT_CREATE, ORG_MEMBERSHIP_REQUEST);

    private static final Set<Permission> ORG_REPRESENTATIVE = union(STUDENT, EnumSet.of(ORG_CHANGE_PROPOSE));

    private static final Set<Permission> MODERATOR = EnumSet.of(
            MODERATION_QUEUE_READ, MODERATION_TAKE, MODERATION_DECIDE, MODERATION_ESCALATE,
            REPORT_REVIEW, MODERATION_AUDIT_READ, ORG_MEMBERSHIP_VERIFY);

    private static final Set<Permission> SENIOR_MODERATOR = union(MODERATOR, EnumSet.of(
            MODERATION_ESCALATED_DECIDE, ROLE_MODERATOR_MANAGE, ACCOUNT_BLOCK, SESSION_REVOKE_ANY,
            IDENTITY_AUDIT_READ, USER_READ_ANY));

    private static final Map<Role, Set<Permission>> BY_ROLE = new EnumMap<>(Map.of(
            Role.STUDENT, STUDENT,
            Role.ORG_REPRESENTATIVE, ORG_REPRESENTATIVE,
            Role.MODERATOR, MODERATOR,
            Role.SENIOR_MODERATOR, SENIOR_MODERATOR));

    private RolePermissions() {
    }

    public static Set<Permission> of(Collection<Role> roles) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> permissions.addAll(BY_ROLE.get(role)));
        return permissions;
    }

    private static Set<Permission> union(Set<Permission> base, Set<Permission> extra) {
        Set<Permission> result = EnumSet.copyOf(base);
        result.addAll(extra);
        return result;
    }
}
