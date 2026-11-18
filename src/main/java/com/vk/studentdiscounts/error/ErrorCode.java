package com.vk.studentdiscounts.error;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Ошибки API. Клиент ориентируется на поле {@code code} в ответе, а не на текст {@code detail}.
 */
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Нужен действующий access-токен"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Пользователь с таким email уже зарегистрирован"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Неверный email или пароль"),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN, "Аккаунт заблокирован"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh-токен недействителен или истёк"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Сессия не найдена"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Пользователь не найден"),
    SELF_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Нельзя менять свои роли, блокировать себя или завершать свои сессии этим методом"),
    ROLE_NOT_MANAGEABLE(HttpStatus.FORBIDDEN, "Эту роль нельзя назначить или отозвать через API"),
    TARGET_NOT_MANAGEABLE(HttpStatus.FORBIDDEN, "Нельзя блокировать старшего модератора или завершать его сессии");

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
