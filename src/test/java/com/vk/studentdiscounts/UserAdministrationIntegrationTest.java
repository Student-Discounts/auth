package com.vk.studentdiscounts;

import com.jayway.jsonpath.JsonPath;
import com.vk.studentdiscounts.admin.SeniorModeratorBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdministrationIntegrationTest extends ApiIntegrationTest {

    private static final String SENIOR = "senior@example.com";
    private static final String ANNA = "anna@example.com";
    private static final String BORIS = "boris@example.com";
    private static final String BLOCK_REASON = """
            {"reason": "Массовая рассылка спама в заявках"}
            """;

    @Autowired
    private SeniorModeratorBootstrap bootstrap;

    private String seniorId;
    private Tokens senior;

    @BeforeEach
    void createSeniorModerator() throws Exception {
        seniorId = registerUser(SENIOR);
        bootstrap.apply(List.of(SENIOR));
        senior = loginUser(SENIOR);
    }

    @Test
    void seniorModeratorGrantsAndRevokesModeratorRoleWithAudit() throws Exception {
        String annaId = registerUser(ANNA);
        Tokens anna = loginUser(ANNA);

        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId)
                        .header(AUTHORIZATION, bearer(senior))
                        .header("X-Correlation-ID", "grant-anna-1"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Correlation-ID", "grant-anna-1"));

        // Новая роль попадает в токен при следующем обновлении.
        Tokens moderator = Tokens.from(refresh(anna.refresh()).andExpect(status().isOk()));
        assertThat(roles(moderator)).containsExactly("MODERATOR", "STUDENT");

        mvc.perform(delete("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isNoContent());
        assertThat(roles(Tokens.from(refresh(moderator.refresh()).andExpect(status().isOk()))))
                .containsExactly("STUDENT");

        mvc.perform(get("/api/v1/users/{id}/identity-events", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", contains("ROLE_REVOKED", "ROLE_GRANTED")))
                .andExpect(jsonPath("$[1].actorId").value(seniorId))
                .andExpect(jsonPath("$[1].payload.role").value("MODERATOR"))
                .andExpect(jsonPath("$[1].correlationId").value("grant-anna-1"));
    }

    @Test
    void grantingExistingRoleIsNoOp() throws Exception {
        String annaId = registerUser(ANNA);

        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(senior)))
                    .andExpect(status().isNoContent());
        }

        assertThat(auditEventCount(annaId)).isEqualTo(1);
    }

    @Test
    void studentAndModeratorCannotUseAdministrationApi() throws Exception {
        String annaId = registerUser(ANNA);
        String borisId = registerUser(BORIS);
        Tokens boris = loginUser(BORIS);

        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(boris)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        mvc.perform(get("/api/v1/users/{id}/identity-events", annaId).header(AUTHORIZATION, bearer(boris)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").param("email", ANNA).header(AUTHORIZATION, bearer(boris)))
                .andExpect(status().isForbidden());

        // Обычный модератор не блокирует аккаунты и не назначает модераторов.
        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", borisId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isNoContent());
        Tokens moderator = loginUser(BORIS);
        mvc.perform(post("/api/v1/users/{id}/block", annaId).header(AUTHORIZATION, bearer(moderator))
                        .contentType(APPLICATION_JSON).content(BLOCK_REASON))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(moderator)))
                .andExpect(status().isForbidden());
    }

    @Test
    void seniorModeratorCannotGrantSeniorRoleOrManageThemselves() throws Exception {
        String annaId = registerUser(ANNA);

        mvc.perform(put("/api/v1/users/{id}/roles/SENIOR_MODERATOR", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("role_not_manageable"));
        mvc.perform(delete("/api/v1/users/{id}/roles/STUDENT", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("role_not_manageable"));
        mvc.perform(delete("/api/v1/users/{id}/roles/SENIOR_MODERATOR", seniorId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", seniorId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("self_management_forbidden"));
        mvc.perform(post("/api/v1/users/{id}/block", seniorId).header(AUTHORIZATION, bearer(senior))
                        .contentType(APPLICATION_JSON).content(BLOCK_REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("self_management_forbidden"));
        mvc.perform(put("/api/v1/users/{id}/roles/ADMIN", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokedRightsStopWorkingBeforeAccessTokenExpires() throws Exception {
        String annaId = registerUser(ANNA);
        // Роль отозвана в БД, но в ещё действующем токене она есть.
        jdbc.update("delete from user_roles where role = 'SENIOR_MODERATOR'");

        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
        assertThat(auditEventCount(annaId)).isZero();
    }

    @Test
    void blockingEndsSessionsAndPreventsLoginUntilUnblocked() throws Exception {
        String annaId = registerUser(ANNA);
        Tokens anna = loginUser(ANNA);

        mvc.perform(post("/api/v1/users/{id}/block", annaId).header(AUTHORIZATION, bearer(senior))
                        .contentType(APPLICATION_JSON).content(BLOCK_REASON))
                .andExpect(status().isNoContent());

        refresh(anna.refresh()).andExpect(status().isUnauthorized());
        login(ANNA, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_blocked"));
        mvc.perform(get("/api/v1/users/{id}", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedReason").value("Массовая рассылка спама в заявках"))
                .andExpect(jsonPath("$.blockedBy").value(seniorId));

        mvc.perform(post("/api/v1/users/{id}/unblock", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isNoContent());

        loginUser(ANNA);
        mvc.perform(get("/api/v1/users").param("email", "Anna@Example.com").header(AUTHORIZATION, bearer(senior)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blockedReason").value(nullValue()));
        mvc.perform(get("/api/v1/users/{id}/identity-events", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(jsonPath("$[*].action", contains("ACCOUNT_UNBLOCKED", "ACCOUNT_BLOCKED")))
                .andExpect(jsonPath("$[1].reason").value("Массовая рассылка спама в заявках"))
                .andExpect(jsonPath("$[1].payload.revokedSessions").value(1));
    }

    @Test
    void blockRequiresReasonAndDoesNotApplyToSeniorModerators() throws Exception {
        String annaId = registerUser(ANNA);
        String borisId = registerUser(BORIS);
        bootstrap.apply(List.of(BORIS));

        mvc.perform(post("/api/v1/users/{id}/block", annaId).header(AUTHORIZATION, bearer(senior))
                        .contentType(APPLICATION_JSON).content("""
                                {"reason": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mvc.perform(post("/api/v1/users/{id}/block", borisId).header(AUTHORIZATION, bearer(senior))
                        .contentType(APPLICATION_JSON).content(BLOCK_REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("target_not_manageable"));
        mvc.perform(post("/api/v1/users/{id}/block", "00000000-0000-0000-0000-000000000000")
                        .header(AUTHORIZATION, bearer(senior))
                        .contentType(APPLICATION_JSON).content(BLOCK_REASON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"));
    }

    @Test
    void seniorModeratorEndsAllSessionsOfUser() throws Exception {
        String annaId = registerUser(ANNA);
        Tokens laptop = loginUser(ANNA);
        Tokens phone = loginUser(ANNA);

        mvc.perform(delete("/api/v1/users/{id}/sessions", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isNoContent());

        refresh(laptop.refresh()).andExpect(status().isUnauthorized());
        refresh(phone.refresh()).andExpect(status().isUnauthorized());
        // Аккаунт не заблокирован: войти заново можно.
        loginUser(ANNA);
        mvc.perform(get("/api/v1/users/{id}/identity-events", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(jsonPath("$[0].action").value("SESSIONS_REVOKED"))
                .andExpect(jsonPath("$[0].payload.revokedSessions").value(2));
    }

    @Test
    void auditLogIsAppendOnly() throws Exception {
        String annaId = registerUser(ANNA);
        mvc.perform(put("/api/v1/users/{id}/roles/MODERATOR", annaId).header(AUTHORIZATION, bearer(senior)))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> jdbc.update("update identity_audit_events set reason = 'подмена'"))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from identity_audit_events"))
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void bootstrapGrantsSeniorModeratorOnceAndSkipsUnregisteredEmail() throws Exception {
        bootstrap.apply(List.of(SENIOR, "nobody@example.com"));

        assertThat(roles(senior)).containsExactly("SENIOR_MODERATOR", "STUDENT");
        assertThat(auditEventCount(seniorId)).isEqualTo(1);
        String actor = JsonPath.read(body(mvc.perform(get("/api/v1/users/{id}/identity-events", seniorId)
                .header(AUTHORIZATION, bearer(senior)))), "$[0].actorId");
        assertThat(actor).isNull();
    }

    private int auditEventCount(String userId) {
        return jdbc.queryForObject("select count(*) from identity_audit_events where subject_user_id = ?::uuid",
                Integer.class, userId);
    }
}
