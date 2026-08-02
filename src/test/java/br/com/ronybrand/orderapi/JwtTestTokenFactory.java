package br.com.ronybrand.orderapi;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds and signs (RS256) test JWTs against an in-memory generated RSA key pair - no key is read
 * from disk and no call to a real Keycloak happens. {@link TestSecurityConfig} decodes against
 * the public key of the same pair.
 */
public final class JwtTestTokenFactory {

    public static final String EXPECTED_AUDIENCE = "order-api";
    private static final String ISSUER = "https://test-issuer.local";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    private JwtTestTokenFactory() {
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static String userToken() {
        return tokenWithRoles(List.of("USER"), EXPECTED_AUDIENCE);
    }

    public static String adminToken() {
        return tokenWithRoles(List.of("ADMIN"), EXPECTED_AUDIENCE);
    }

    public static String tokenWithInvalidAudience() {
        return tokenWithRoles(List.of("USER"), "some-other-audience");
    }

    public static String tokenWithRoles(final List<String> roles, final String audience) {
        final Instant now = Instant.now();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("realm_access", Map.of("roles", roles))
                .build();
        return sign(claims);
    }

    private static String sign(final JWTClaimsSet claims) {
        try {
            final SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            signedJwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return signedJwt.serialize();
        } catch (final JOSEException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate RSA key pair for test JWTs", e);
        }
    }
}
