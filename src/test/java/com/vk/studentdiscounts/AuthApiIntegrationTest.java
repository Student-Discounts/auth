package com.vk.studentdiscounts;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends ApiIntegrationTest {

    private static final String ANNA = "anna@example.com";
    private static final String BORIS = "boris@example.com";

    @Test
    void registerCreatesStudentAndStoresOnlyPasswordHash() throws Exception {
        register(ANNA, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(ANNA))
                .andExpect(jsonPath("$.roles", contains("STUDENT")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String storedHash = jdbc.queryForObject("select password_hash from users", String.class);
        assertThat(storedHash).startsWith("{argon2}").doesNotContain(PASSWORD);
    }

    @Test
    void registerRejectsSameEmailInDifferentCase() throws Exception {
        register(ANNA, PASSWORD).andExpect(status().isCreated());

        register("Anna@Example.COM", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"));
    }

    @Test
    void registerReturnsErrorsForEachInvalidField() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "displayName": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void loginFailsTheSameWayForWrongPasswordAndUnknownEmail() throws Exception {
        registerUser(ANNA);

        login(ANNA, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void accessTokenFollowsContract() throws Exception {
        String userId = registerUser(ANNA);

        Jwt jwt = jwtDecoder.decode(loginUser(ANNA).access());

        assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("http://localhost:8080");
        assertThat(jwt.getAudience()).containsExactly("student-discounts-api");
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("STUDENT");
        assertThat(jwt.getClaimAsString("sid")).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer not-a-jwt")).andExpect(status().isUnauthorized());

        registerUser(ANNA);
        mvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(loginUser(ANNA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ANNA));
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldTokenRevokesSession() throws Exception {
        registerUser(ANNA);
        Tokens first = loginUser(ANNA);

        Tokens second = Tokens.from(refresh(first.refresh()).andExpect(status().isOk()));
        assertThat(second.refresh()).isNotEqualTo(first.refresh());

        refresh(first.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh_token"));
        // Сессия отозвана целиком: не работает и токен, полученный при последней ротации.
        refresh(second.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsCurrentSession() throws Exception {
        registerUser(ANNA);
        Tokens tokens = loginUser(ANNA);

        mvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isNoContent());

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void userCanListAndEndOwnSessions() throws Exception {
        registerUser(ANNA);
        Tokens laptop = loginUser(ANNA);
        Tokens phone = loginUser(ANNA);

        mvc.perform(get("/api/v1/me/sessions").header(AUTHORIZATION, bearer(laptop)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.current == true)].id", contains(sessionId(laptop))));

        mvc.perform(delete("/api/v1/me/sessions/{id}", sessionId(phone)).header(AUTHORIZATION, bearer(laptop)))
                .andExpect(status().isNoContent());
        refresh(phone.refresh()).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/sessions").header(AUTHORIZATION, bearer(laptop)))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/v1/me/sessions").header(AUTHORIZATION, bearer(laptop)))
                .andExpect(status().isNoContent());
        refresh(laptop.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotEndSomeoneElsesSession() throws Exception {
        registerUser(ANNA);
        registerUser(BORIS);
        Tokens anna = loginUser(ANNA);
        Tokens boris = loginUser(BORIS);

        mvc.perform(delete("/api/v1/me/sessions/{id}", sessionId(anna)).header(AUTHORIZATION, bearer(boris)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("session_not_found"));

        refresh(anna.refresh()).andExpect(status().isOk());
    }

    @Test
    void blockedUserCannotLoginAndLosesExistingSessions() throws Exception {
        registerUser(ANNA);
        Tokens tokens = loginUser(ANNA);
        jdbc.update("update users set status = 'BLOCKED' where email = ?", ANNA);

        login(ANNA, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_blocked"));
        refresh(tokens.refresh()).andExpect(status().isForbidden());

        // Отзыв сессии сохранился, несмотря на ошибку в ответе: после разблокировки нужен новый вход.
        jdbc.update("update users set status = 'ACTIVE' where email = ?", ANNA);
        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void jwksPublishesOnlyPublicSigningKey() throws Exception {
        registerUser(ANNA);
        Jwt jwt = jwtDecoder.decode(loginUser(ANNA).access());

        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value(jwt.getHeaders().get("kid")))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
