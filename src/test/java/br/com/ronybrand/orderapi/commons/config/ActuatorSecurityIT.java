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
 * liveness/readiness probes can't present one); everything else exposed
 * (see {@code application.yml}'s {@code management.endpoints.web.exposure.include}), like
 * {@code /actuator/prometheus}, stays behind the same authentication as the rest of the API.
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
    void health_ShouldExposeComponentDetail_WhenAuthenticated() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, request(authHeadersForUser()), String.class);

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
    void prometheus_ShouldReturn200_WhenAuthenticated() {
        final ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, request(authHeadersForUser()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
