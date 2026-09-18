package br.com.ronybrand.orderapi.docs;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Springdoc stays disabled outside the {@code dev} profile in every real deployment (see
 * {@code SwaggerDisabledByDefaultTest}) - there's no live instance of this app to browse
 * {@code /swagger-ui.html} on. This test only re-enables {@code springdoc.api-docs.enabled} for
 * its own {@code @SpringBootTest} context (never touches {@code application.yml}), hits
 * {@code /v3/api-docs} against the full context already booted for the rest of the {@code *IT}
 * suite (same Testcontainers Postgres/Mongo/Redis/RabbitMQ, no Keycloak needed - see
 * {@link AbstractAuthIntegrationTest}), and drops the raw spec at {@code target/openapi/openapi.json}.
 * A CI step (not this test) turns that file into the published GitHub Pages docs - see
 * {@code .github/workflows/ci.yml}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiSpecExportIT extends AbstractAuthIntegrationTest {

    private static final Path OUTPUT_FILE = Path.of("target", "openapi", "openapi.json");

    @Test
    void exportsTheOpenApiSpecToTargetOpenapiJson() throws IOException {
        final ResponseEntity<String> response =
                restTemplate.exchange("/v3/api-docs", HttpMethod.GET, request(authHeadersForAdmin()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();

        Files.createDirectories(OUTPUT_FILE.getParent());
        Files.writeString(OUTPUT_FILE, response.getBody(), StandardCharsets.UTF_8);
    }
}
