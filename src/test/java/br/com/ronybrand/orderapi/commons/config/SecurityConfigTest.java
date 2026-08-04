package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    private final SecurityConfig.RealmRoleConverter converter = new SecurityConfig.RealmRoleConverter();

    @Test
    void convert_ShouldMapRealmAccessRoles_ToUppercasedRoleAuthorities() {
        final Jwt jwt = jwtWithRealmRoles(List.of("user", "admin"));

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void convert_ShouldReturnEmpty_WhenRealmAccessClaimIsAbsent() {
        final Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void convert_ShouldIgnoreNonStringEntries_WhenRolesClaimHasUnexpectedElementTypes() {
        final Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-id")
                .claim("realm_access", Map.of("roles", List.of("admin", 42, Map.of("nested", "value"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        final Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void convert_ShouldReturnEmpty_WhenRolesListIsMissingFromRealmAccess() {
        final Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-id")
                .claim("realm_access", Map.of("not-roles", List.of("x")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private Jwt jwtWithRealmRoles(final List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-id")
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
