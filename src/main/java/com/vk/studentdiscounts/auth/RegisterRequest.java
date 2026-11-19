package com.vk.studentdiscounts.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 8, max = 128) String password,
                              @NotBlank @Size(max = 100) String displayName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=%s, displayName=%s]".formatted(email, displayName);
    }
}
