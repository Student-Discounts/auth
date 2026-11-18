package com.vk.studentdiscounts.admin;

import com.vk.studentdiscounts.authz.Permission;
import com.vk.studentdiscounts.authz.RolePermissions;
import com.vk.studentdiscounts.error.ApiException;
import com.vk.studentdiscounts.error.ErrorCode;
import com.vk.studentdiscounts.session.SessionRevokeReason;
import com.vk.studentdiscounts.session.SessionService;
import com.vk.studentdiscounts.user.Role;
import com.vk.studentdiscounts.user.User;
import com.vk.studentdiscounts.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Назначение ролей, блокировка и завершение чужих сессий (FR-003, FR-007, FR-008).
 * Каждое изменение записывается в журнал аудита в той же транзакции.
 */
@Service
public class UserAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(UserAdministrationService.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final UserRepository users;
    private final SessionService sessions;
    private final IdentityAudit audit;
    private final IdentityAuditEventRepository events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public UserAdministrationService(UserRepository users, SessionService sessions, IdentityAudit audit,
                                     IdentityAuditEventRepository events, ObjectMapper mapper, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserAdminResponse find(UUID userId) {
        return users.findById(userId).map(UserAdminResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserAdminResponse findByEmail(String email) {
        return users.findByEmail(User.normalizeEmail(email)).map(UserAdminResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Повторное назначение уже имеющейся роли ничего не меняет и в аудит не пишется.
     * Новая роль попадёт в access-токен пользователя при следующем обновлении токена.
     */
    @Transactional
    public void grantRole(UUID actorId, UUID userId, Role role) {
        requireCurrentPermission(actorId, Permission.ROLE_MODERATOR_MANAGE);
        requireManageable(role);
        User user = loadOtherForUpdate(actorId, userId);
        if (user.grantRole(role)) {
            audit.record(userId, IdentityAuditAction.ROLE_GRANTED, actorId, null, Map.of("role", role.name()));
        }
    }

    @Transactional
    public void revokeRole(UUID actorId, UUID userId, Role role) {
        requireCurrentPermission(actorId, Permission.ROLE_MODERATOR_MANAGE);
        requireManageable(role);
        User user = loadOtherForUpdate(actorId, userId);
        if (user.revokeRole(role)) {
            audit.record(userId, IdentityAuditAction.ROLE_REVOKED, actorId, null, Map.of("role", role.name()));
        }
    }

    /**
     * Блокировка сразу завершает все сессии: обновить токен нельзя, уже выданный access-токен
     * действует до своего срока (≤ 5 минут). Повторная блокировка ничего не меняет.
     */
    @Transactional
    public void block(UUID actorId, UUID userId, String reason) {
        requireCurrentPermission(actorId, Permission.ACCOUNT_BLOCK);
        User user = loadOtherForUpdate(actorId, userId);
        requireNotSeniorModerator(user);
        if (user.isBlocked()) {
            return;
        }
        user.block(actorId, reason, clock.instant());
        int revoked = sessions.revokeAll(userId, SessionRevokeReason.ACCOUNT_BLOCKED);
        audit.record(userId, IdentityAuditAction.ACCOUNT_BLOCKED, actorId, reason, Map.of("revokedSessions", revoked));
    }

    @Transactional
    public void unblock(UUID actorId, UUID userId) {
        requireCurrentPermission(actorId, Permission.ACCOUNT_BLOCK);
        User user = loadOtherForUpdate(actorId, userId);
        if (!user.isBlocked()) {
            return;
        }
        user.unblock();
        audit.record(userId, IdentityAuditAction.ACCOUNT_UNBLOCKED, actorId, null, Map.of());
    }

    @Transactional
    public void revokeSessions(UUID actorId, UUID userId) {
        requireCurrentPermission(actorId, Permission.SESSION_REVOKE_ANY);
        User user = loadOtherForUpdate(actorId, userId);
        requireNotSeniorModerator(user);
        int revoked = sessions.revokeAll(userId, SessionRevokeReason.REVOKED_BY_ADMIN);
        audit.record(userId, IdentityAuditAction.SESSIONS_REVOKED, actorId, null, Map.of("revokedSessions", revoked));
    }

    @Transactional(readOnly = true)
    public List<IdentityEventResponse> events(UUID userId, int limit) {
        if (!users.existsById(userId)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return events.findBySubjectUserIdOrderByCreatedAtDescIdDesc(userId, Limit.of(limit)).stream()
                .map(event -> IdentityEventResponse.from(event, mapper.readValue(event.getPayload(), PAYLOAD_TYPE)))
                .toList();
    }

    /**
     * Назначает старшего модератора без API: через API эту роль выдать нельзя (условие C-6).
     *
     * @return {@code false}, если пользователь с таким email не зарегистрирован
     */
    @Transactional
    public boolean grantSeniorModeratorAtStartup(String email) {
        User user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.grantRole(Role.SENIOR_MODERATOR)) {
            audit.record(user.getId(), IdentityAuditAction.ROLE_GRANTED, null,
                    "Начальное назначение через sd.auth.bootstrap.senior-moderators",
                    Map.of("role", Role.SENIOR_MODERATOR.name()));
            log.info("Пользователю {} назначена роль SENIOR_MODERATOR при запуске", user.getId());
        }
        return true;
    }

    /**
     * Условие C-7: разрешение из токена перепроверяется по БД. Токен мог быть выпущен до того,
     * как у актора отозвали роль или его заблокировали, и действует ещё до 5 минут.
     */
    private void requireCurrentPermission(UUID actorId, Permission permission) {
        User actor = users.findById(actorId).orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
        if (actor.isBlocked() || !RolePermissions.of(actor.getRoles()).contains(permission)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * Условие C-6: свои роли, свою блокировку и свои сессии через этот API менять нельзя.
     */
    private User loadOtherForUpdate(UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new ApiException(ErrorCode.SELF_MANAGEMENT_FORBIDDEN);
        }
        return users.findForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Через API управляется только роль модератора. Студент выдаётся при регистрации, представитель —
     * после подтверждения связи с организацией, старший модератор — только при развёртывании.
     */
    private static void requireManageable(Role role) {
        if (role != Role.MODERATOR) {
            throw new ApiException(ErrorCode.ROLE_NOT_MANAGEABLE);
        }
    }

    private static void requireNotSeniorModerator(User user) {
        if (user.getRoles().contains(Role.SENIOR_MODERATOR)) {
            throw new ApiException(ErrorCode.TARGET_NOT_MANAGEABLE);
        }
    }
}
