package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@code /actuator/health} must stay reachable without a Bearer token (container
 * liveness/readiness probes can't present one) - but only its aggregate UP/DOWN status. Everything
 * that leaks operational detail - component-level health, and {@code /actuator/prometheus} itself
 * (queue/retry/idempotency/outbox-backlog counters) - is restricted to the {@code ADMIN} role, not
 * just any authenticated caller: a regular customer-facing API user has no operational need to see
 * it (see {@code application.yml}'s {@code management.endpoint.health.roles} and this class's
 * {@code SecurityConfig}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class ActuatorSecurityIT extends AbstractAuthIntegrationTest {

    @Test
    void health_ShouldReturn200_WhenUnauthenticated() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, request(headers()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void health_ShouldNotExposeComponentDetail_WhenUnauthenticated() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, request(headers()), String.class);

        assertThat(response.getBody()).doesNotContain("\"components\"");
    }

    @Test
    void health_ShouldNotExposeComponentDetail_WhenAuthenticatedAsRegularUser() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, request(authHeadersForUser()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("\"components\"");
    }

    @Test
    void health_ShouldExposeComponentDetail_WhenAuthenticatedAsAdmin() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, request(authHeadersForAdmin()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"components\"");
    }

    @Test
    void prometheus_ShouldReturn401_WhenUnauthenticated() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, request(headers()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void prometheus_ShouldReturn403_WhenAuthenticatedAsRegularUser() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, request(authHeadersForUser()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void prometheus_ShouldReturn200_WhenAuthenticatedAsAdmin() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, request(authHeadersForAdmin()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
