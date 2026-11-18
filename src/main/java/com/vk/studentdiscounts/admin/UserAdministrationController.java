package com.vk.studentdiscounts.admin;

import com.vk.studentdiscounts.token.TokenClaims;
import com.vk.studentdiscounts.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Администрирование пользователей. Доступ по разрешениям из матрицы прав;
 * изменения дополнительно перепроверяют права актора по БД (условие C-7).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Администрирование пользователей", description = "Только для старшего модератора")
public class UserAdministrationController {

    private static final int MAX_EVENTS = 200;

    private final UserAdministrationService administration;

    public UserAdministrationController(UserAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping
    @Operation(summary = "Найти пользователя по email")
    @PreAuthorize("hasAuthority('USER_READ_ANY')")
    public UserAdminResponse findByEmail(@RequestParam String email) {
        return administration.findByEmail(email);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Пользователь со статусом и причиной блокировки")
    @PreAuthorize("hasAuthority('USER_READ_ANY')")
    public UserAdminResponse find(@PathVariable UUID userId) {
        return administration.find(userId);
    }

    @PutMapping("/{userId}/roles/{role}")
    @Operation(summary = "Назначить роль; через API доступна только MODERATOR")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_MODERATOR_MANAGE')")
    public void grantRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @PathVariable Role role) {
        administration.grantRole(TokenClaims.userId(jwt), userId, role);
    }

    @DeleteMapping("/{userId}/roles/{role}")
    @Operation(summary = "Снять роль; через API доступна только MODERATOR")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_MODERATOR_MANAGE')")
    public void revokeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @PathVariable Role role) {
        administration.revokeRole(TokenClaims.userId(jwt), userId, role);
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "Заблокировать с причиной; все сессии пользователя завершаются")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACCOUNT_BLOCK')")
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                      @Valid @RequestBody BlockRequest request) {
        administration.block(TokenClaims.userId(jwt), userId, request.reason().strip());
    }

    @PostMapping("/{userId}/unblock")
    @Operation(summary = "Разблокировать")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACCOUNT_BLOCK')")
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        administration.unblock(TokenClaims.userId(jwt), userId);
    }

    @DeleteMapping("/{userId}/sessions")
    @Operation(summary = "Завершить все сессии пользователя")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SESSION_REVOKE_ANY')")
    public void revokeSessions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        administration.revokeSessions(TokenClaims.userId(jwt), userId);
    }

    /**
     * История ролей, блокировок и отзыва сессий, новые события первыми.
     */
    @GetMapping("/{userId}/identity-events")
    @Operation(summary = "История ролей, блокировок и отзыва сессий, новые первыми")
    @PreAuthorize("hasAuthority('IDENTITY_AUDIT_READ')")
    public List<IdentityEventResponse> events(@PathVariable UUID userId,
                                              @RequestParam(defaultValue = "50") int limit) {
        return administration.events(userId, Math.clamp(limit, 1, MAX_EVENTS));
    }
}
