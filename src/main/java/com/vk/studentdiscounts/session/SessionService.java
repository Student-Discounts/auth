package com.vk.studentdiscounts.session;

import com.vk.studentdiscounts.config.AuthProperties;
import com.vk.studentdiscounts.token.AccessTokenIssuer;
import com.vk.studentdiscounts.user.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class SessionService {

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final UserSessionRepository sessions;
    private final AccessTokenIssuer accessTokens;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions,
                          AccessTokenIssuer accessTokens,
                          AuthProperties properties,
                          Clock clock) {
        this.sessions = sessions;
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
