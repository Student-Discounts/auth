package com.vk.studentdiscounts.session;

/**
 * @param expiresIn        сколько секунд действует access-токен
 * @param refreshExpiresIn сколько секунд осталось до окончания сессии
 */
public record TokenResponse(String accessToken,
                            String tokenType,
                            long expiresIn,
                            String refreshToken,
                            long refreshExpiresIn) {

    // Токены не должны попасть в логи (NFR-019).
    @Override
    public String toString() {
        return "TokenResponse[tokenType=%s, expiresIn=%d, refreshExpiresIn=%d]"
                .formatted(tokenType, expiresIn, refreshExpiresIn);
    }
}
