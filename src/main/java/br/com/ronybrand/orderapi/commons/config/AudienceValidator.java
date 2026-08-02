package br.com.ronybrand.orderapi.commons.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Spring's default JWT validation only checks issuer/expiration/not-before - the {@code aud}
 * claim is never checked unless this validator is added explicitly. Without it, any token issued
 * by the same Keycloak realm, but for a different client/audience, would be accepted here.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public AudienceValidator(final String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt token) {
        if (token.getAudience().contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        final OAuth2Error error = new OAuth2Error("invalid_token",
                "The required audience '" + expectedAudience + "' is missing", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
