package com.vk.studentdiscounts.authz;

import com.vk.studentdiscounts.user.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.vk.studentdiscounts.authz.Permission.ACCOUNT_BLOCK;
import static com.vk.studentdiscounts.authz.Permission.MODERATION_DECIDE;
import static com.vk.studentdiscounts.authz.Permission.ORG_CHANGE_PROPOSE;
import static com.vk.studentdiscounts.authz.Permission.ROLE_MODERATOR_MANAGE;
import static com.vk.studentdiscounts.authz.Permission.SUBMISSION_CREATE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Правила из docs/design/RBAC.md, разделы 3 и 4.
 */
class RolePermissionsTest {

    @Test
    void seniorModeratorHasEveryModeratorPermission() {
        Set<Permission> moderator = RolePermissions.of(List.of(Role.MODERATOR));
        Set<Permission> senior = RolePermissions.of(List.of(Role.SENIOR_MODERATOR));

        assertThat(senior).containsAll(moderator).contains(ROLE_MODERATOR_MANAGE, ACCOUNT_BLOCK);
        assertThat(moderator).doesNotContain(ROLE_MODERATOR_MANAGE, ACCOUNT_BLOCK);
    }

    @Test
    void moderatorsCannotCreateSubmissionsAndStudentsCannotModerate() {
        assertThat(RolePermissions.of(List.of(Role.MODERATOR, Role.SENIOR_MODERATOR))).doesNotContain(SUBMISSION_CREATE);
        assertThat(RolePermissions.of(List.of(Role.STUDENT))).doesNotContain(MODERATION_DECIDE, ORG_CHANGE_PROPOSE);
    }

    @Test
    void permissionsOfSeveralRolesAreCombined() {
        assertThat(RolePermissions.of(List.of(Role.STUDENT, Role.MODERATOR)))
                .contains(SUBMISSION_CREATE, MODERATION_DECIDE);
        assertThat(RolePermissions.of(List.of())).isEmpty();
    }
}
