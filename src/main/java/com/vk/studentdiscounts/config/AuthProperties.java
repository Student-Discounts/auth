package com.vk.studentdiscounts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("sd.auth")
public record AuthProperties(@Valid @NotNull Jwt jwt,
                             @DefaultValue Session session,
                             @DefaultValue Cors cors) {

    /**
     * @param issuer         значение claim {@code iss}; сервисы-потребители сверяют его
     * @param audience       значение claim {@code aud}
     * @param accessTokenTtl срок жизни access-токена
     * @param privateKey     PEM-файл закрытого ключа RSA (PKCS#8); если ключи не заданы, при старте генерируется временная пара
     * @param publicKey      PEM-файл открытого ключа RSA (X.509)
     */
    public record Jwt(@NotBlank String issuer,
                      @NotBlank String audience,
                      @DefaultValue("5m") Duration accessTokenTtl,
                      Resource privateKey,
                      Resource publicKey) {
    }

    /**
     * @param refreshTokenTtl срок жизни сессии с момента входа; обновление токена его не продлевает
     */
    public record Session(@DefaultValue("30d") Duration refreshTokenTtl) {
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }
}
