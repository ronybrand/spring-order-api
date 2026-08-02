package br.com.ronybrand.orderapi;

import br.com.ronybrand.orderapi.commons.config.AudienceValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces the production {@code JwtDecoder} (which performs OIDC discovery against a real
 * Keycloak) with a decoder backed by the in-memory RSA key pair from {@link JwtTestTokenFactory} -
 * same audience validation as production ({@link AudienceValidator} reused, not reimplemented),
 * without depending on any external service running.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtTestTokenFactory.publicKey()).build();
        final OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        final OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(JwtTestTokenFactory.EXPECTED_AUDIENCE);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, audienceValidator));
        return decoder;
    }
}
