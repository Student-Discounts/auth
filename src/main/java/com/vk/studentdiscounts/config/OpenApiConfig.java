package com.vk.studentdiscounts.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Спецификация OpenAPI (NFR-037). Swagger UI: {@code /swagger-ui.html}. По умолчанию каждая операция
 * требует bearer-токен; публичные операции снимают это требование через {@code @SecurityRequirements}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "sd-auth", version = "v1",
                description = "Аутентификация, сессии и администрирование пользователей платформы студенческих скидок"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "accessToken из ответа POST /api/v1/auth/login или /refresh")
public class OpenApiConfig {

    static final String BEARER = "bearer";
}
