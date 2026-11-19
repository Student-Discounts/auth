package com.vk.studentdiscounts;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Общая основа интеграционных тестов API: PostgreSQL в Testcontainers, пустая БД перед каждым тестом
 * и помощники для регистрации, входа и обновления токенов.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class ApiIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate table users cascade");
    }

    protected ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "displayName": "Тестовый пользователь"}
                """.formatted(email, password)));
    }

    protected String registerUser(String email) throws Exception {
        return JsonPath.read(body(register(email, PASSWORD).andExpect(status().isCreated())), "$.id");
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    protected Tokens loginUser(String email) throws Exception {
        return Tokens.from(login(email, PASSWORD).andExpect(status().isOk()));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON).content("""
                {"refreshToken": "%s"}
                """.formatted(refreshToken)));
    }

    protected String sessionId(Tokens tokens) {
        return jwtDecoder.decode(tokens.access()).getClaimAsString("sid");
    }

    protected List<String> roles(Tokens tokens) {
        return jwtDecoder.decode(tokens.access()).getClaimAsStringList("roles");
    }

    protected static String bearer(Tokens tokens) {
        return "Bearer " + tokens.access();
    }

    protected static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected record Tokens(String access, String refresh) {

        static Tokens from(ResultActions result) throws Exception {
            String json = body(result);
            return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
        }
    }
}
