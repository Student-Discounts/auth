package com.vk.studentdiscounts.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ограничение длины пароля защищает от запросов, которые заставляют сервер хешировать мегабайты данных.
 */
public record LoginRequest(@NotBlank @Size(max = 254) String email,
                           @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=%s]".formatted(email);
    }
}
