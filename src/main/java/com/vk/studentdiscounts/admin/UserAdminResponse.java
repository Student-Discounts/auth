package com.vk.studentdiscounts.admin;

import com.vk.studentdiscounts.user.Role;
import com.vk.studentdiscounts.user.User;
import com.vk.studentdiscounts.user.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Пользователь глазами старшего модератора: в отличие от {@code /api/v1/me}, со статусом и причиной блокировки.
 */
public record UserAdminResponse(UUID id,
                                String email,
                                String displayName,
                                UserStatus status,
                                List<Role> roles,
                                Instant createdAt,
                                Instant blockedAt,
                                String blockedReason,
                                UUID blockedBy) {

    static UserAdminResponse from(User user) {
        return new UserAdminResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                user.getRoles().stream().sorted().toList(), user.getCreatedAt(),
                user.getBlockedAt(), user.getBlockedReason(), user.getBlockedBy());
    }
}
