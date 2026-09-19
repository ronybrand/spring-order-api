package br.com.ronybrand.orderapi.commons.config;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * `spring.jpa.hibernate.ddl-auto` stays {@code none} in every real profile (see
 * {@code application.yml}) - Liquibase, not Hibernate, owns schema changes. Nothing then verifies
 * that the Liquibase changelog and the JPA entity mappings agree: adding a {@code @Column} without
 * a matching changeset (or vice versa) would only surface later, as a runtime SQL error against
 * production data.
 *
 * <p>This test boots the full context - same Testcontainers Postgres, same Liquibase migrations
 * the rest of the {@code *IT} suite already runs against (see {@link AbstractAuthIntegrationTest})
 * - with {@code ddl-auto=validate} overridden just for this test class. Hibernate validates the
 * mapped schema against the actual database at {@code EntityManagerFactory} creation and throws
 * {@code SchemaManagementException} on any mismatch; a successful context load is the assertion.
 * See ADR 0007 for why this runs only here, never against production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidationIT extends AbstractAuthIntegrationTest {

    @Test
    void entityMappingsMatchTheLiquibaseAppliedSchema() {
        // Intentionally empty: @SpringBootTest already failed the test above if
        // ddl-auto=validate rejected the schema during context startup.
    }
}
