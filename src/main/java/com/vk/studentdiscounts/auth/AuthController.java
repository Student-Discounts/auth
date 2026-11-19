package com.vk.studentdiscounts.auth;

import com.vk.studentdiscounts.session.SessionRevokeReason;
import com.vk.studentdiscounts.session.SessionService;
import com.vk.studentdiscounts.session.TokenResponse;
import com.vk.studentdiscounts.token.TokenClaims;
import com.vk.studentdiscounts.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Аутентификация")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    @Operation(summary = "Регистрация; пользователь получает роль STUDENT")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Вход: возвращает access- и refresh-токен")
    @SecurityRequirements
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обмен refresh-токена на новую пару; старый refresh-токен перестаёт действовать")
    @SecurityRequirements
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход: завершает текущую сессию")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        sessionService.revoke(TokenClaims.userId(jwt), TokenClaims.sessionId(jwt), SessionRevokeReason.LOGOUT);
    }
}
