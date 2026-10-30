package com.vk.studentdiscounts.error;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Ошибки API. Клиент ориентируется на поле {@code code} в ответе, а не на текст {@code detail}.
 */
public enum ErrorCode {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Пользователь с таким email уже зарегистрирован"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Неверный email или пароль"),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, "Аккаунт заблокирован"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Пользователь не найден");

    private final HttpStatus status;
    private final String detail;

    ErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
