package com.vk.studentdiscounts.session;

import com.vk.studentdiscounts.token.TokenClaims;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Сессии текущего пользователя. Access-токен завершённой сессии действует до истечения своего срока (≤ 5 минут),
 * но обновить его уже нельзя.
 */
@RestController
@RequestMapping("/api/v1/me/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return sessionService.listActive(TokenClaims.userId(jwt), TokenClaims.sessionId(jwt));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        sessionService.revoke(TokenClaims.userId(jwt), sessionId, SessionRevokeReason.USER_REVOKED);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAll(@AuthenticationPrincipal Jwt jwt) {
        sessionService.revokeAll(TokenClaims.userId(jwt));
    }
}
