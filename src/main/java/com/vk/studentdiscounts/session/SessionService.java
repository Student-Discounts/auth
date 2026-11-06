package com.vk.studentdiscounts.session;

import com.vk.studentdiscounts.config.AuthProperties;
import com.vk.studentdiscounts.error.ApiException;
import com.vk.studentdiscounts.error.ErrorCode;
import com.vk.studentdiscounts.token.AccessTokenIssuer;
import com.vk.studentdiscounts.user.User;
import com.vk.studentdiscounts.user.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final AccessTokenIssuer accessTokens;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions,
                          UserRepository users,
                          AccessTokenIssuer accessTokens,
                          AuthProperties properties,
                          Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokenTtl = properties.session().refreshTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public TokenResponse open(User user, String userAgent) {
        Instant now = clock.instant();
        String refreshToken = RefreshTokens.generate();
        UserSession session = sessions.save(UserSession.open(
                user.getId(), RefreshTokens.hash(refreshToken), truncate(userAgent), now, refreshTokenTtl));
        return issueTokens(user, session, refreshToken, now);
    }

    /**
     * Обменивает refresh-токен на новую пару токенов. Старый refresh-токен после этого недействителен.
     * Если предъявлен уже обменянный токен, его, вероятно, украли: сессия отзывается целиком.
     * Отзыв должен сохраниться, хотя клиент получает ошибку, поэтому транзакция не откатывается на {@link ApiException}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        String hash = RefreshTokens.hash(refreshToken);

        UserSession session = sessions.findByRefreshTokenHash(hash).orElse(null);
        if (session == null) {
            sessions.findByPreviousRefreshTokenHash(hash)
                    .ifPresent(reused -> reused.revoke(SessionRevokeReason.REFRESH_TOKEN_REUSE, now));
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!session.isActive(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = users.findById(session.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (user.isBlocked()) {
            session.revoke(SessionRevokeReason.ACCOUNT_BLOCKED, now);
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED);
        }

        String newRefreshToken = RefreshTokens.generate();
        session.rotate(RefreshTokens.hash(newRefreshToken), now);
        return issueTokens(user, session, newRefreshToken, now);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listActive(UUID userId, UUID currentSessionId) {
        return sessions.findActive(userId, clock.instant()).stream()
                .map(session -> SessionResponse.from(session, currentSessionId))
                .toList();
    }

    /**
     * Завершает сессию пользователя. Чужая сессия неотличима от несуществующей, чтобы не раскрывать её наличие.
     */
    @Transactional
    public void revoke(UUID userId, UUID sessionId, SessionRevokeReason reason) {
        UserSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        session.revoke(reason, clock.instant());
    }

    @Transactional
    public void revokeAll(UUID userId) {
        sessions.revokeAll(userId, SessionRevokeReason.ALL_SESSIONS_REVOKED, clock.instant());
    }

    private TokenResponse issueTokens(User user, UserSession session, String refreshToken, Instant now) {
        Jwt accessToken = accessTokens.issue(user, session.getId(), now);
        return new TokenResponse(
                accessToken.getTokenValue(),
                "Bearer",
                Duration.between(now, accessToken.getExpiresAt()).toSeconds(),
                refreshToken,
                Duration.between(now, session.getExpiresAt()).toSeconds());
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
