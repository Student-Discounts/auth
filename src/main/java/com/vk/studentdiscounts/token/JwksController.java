package com.vk.studentdiscounts.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Открытый ключ подписи. Остальные сервисы скачивают его, чтобы проверять токены локально.
 */
@RestController
@Tag(name = "Ключи подписи")
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey jwtSigningKey) {
        this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Открытый ключ для проверки подписи access-токенов")
    @SecurityRequirements
    public Map<String, Object> jwks() {
        return jwks;
    }
}
