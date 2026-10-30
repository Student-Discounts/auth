package com.vk.studentdiscounts.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.vk.studentdiscounts.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Ключ подписи JWT и компоненты, которые выпускают и проверяют токены этим ключом.
 */
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(AuthProperties properties) throws IOException, JOSEException {
        AuthProperties.Jwt jwt = properties.jwt();
        if (jwt.privateKey() == null && jwt.publicKey() == null) {
            log.warn("Ключи подписи JWT не заданы (sd.auth.jwt.private-key, sd.auth.jwt.public-key): "
                    + "сгенерирована временная пара, токены станут недействительны после перезапуска. "
                    + "Допустимо только для локальной разработки.");
            return new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint(true)
                    .generate();
        }
        if (jwt.privateKey() == null || jwt.publicKey() == null) {
            throw new IllegalStateException("Нужно задать оба ключа: sd.auth.jwt.private-key и sd.auth.jwt.public-key");
        }
        try (InputStream privatePem = jwt.privateKey().getInputStream();
             InputStream publicPem = jwt.publicKey().getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privatePem);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicPem);
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * Проверяет токены на защищённых endpoint самого sd-auth так же, как это делают остальные сервисы:
     * подпись, срок действия, {@code iss} и {@code aud}.
     */
    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, AuthProperties properties) throws JOSEException {
        String audience = properties.jwt().audience();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
        return decoder;
    }
}
