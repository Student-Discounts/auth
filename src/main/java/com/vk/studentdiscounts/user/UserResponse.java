package com.vk.studentdiscounts.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id,
                           String email,
                           String displayName,
                           List<Role> roles,
                           Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRoles().stream().sorted().toList(), user.getCreatedAt());
    }
}
