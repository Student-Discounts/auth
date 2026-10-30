package com.vk.studentdiscounts.session;

public enum SessionRevokeReason {
    /** Пользователь вышел из этой сессии. */
    LOGOUT,
    /** Пользователь завершил сессию из списка своих сессий. */
    USER_REVOKED,
    /** Пользователь завершил все свои сессии. */
    ALL_SESSIONS_REVOKED,
    /** Уже использованный refresh-токен предъявлен повторно: вероятна кража токена. */
    REFRESH_TOKEN_REUSE,
    /** Аккаунт заблокирован. */
    ACCOUNT_BLOCKED
}
