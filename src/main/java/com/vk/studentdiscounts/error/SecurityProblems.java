package com.vk.studentdiscounts.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ответы 401 и 403 от Spring Security в том же формате ProblemDetail, что и остальные ошибки API.
 * Статус и заголовок {@code WWW-Authenticate} выставляют стандартные обработчики Resource Server, здесь добавляется тело.
 */
public final class SecurityProblems {

    private final ObjectMapper mapper;

    public SecurityProblems(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        BearerTokenAuthenticationEntryPoint standard = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            standard.commence(request, response, exception);
            write(request, response, ErrorCode.UNAUTHORIZED);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        BearerTokenAccessDeniedHandler standard = new BearerTokenAccessDeniedHandler();
        return (request, response, exception) -> {
            standard.handle(request, response, exception);
            write(request, response, ErrorCode.ACCESS_DENIED);
        };
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", errorCode.status().getReasonPhrase());
        body.put("status", errorCode.status().value());
        body.put("detail", errorCode.detail());
        body.put("instance", request.getRequestURI());
        body.put("code", errorCode.code());
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
