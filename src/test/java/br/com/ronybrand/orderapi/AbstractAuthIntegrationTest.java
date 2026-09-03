package br.com.ronybrand.orderapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every {@code *ControllerIT}: a single real Postgres and a single real MongoDB
 * (both Testcontainers) serve the whole suite, and authenticated header/request helpers are
 * centralized here, never redefined per test class (DRY principle in tests, see the
 * {@code spring-feature} skill).
 *
 * <p>Deliberately <b>without</b> {@code @Testcontainers}/{@code @Container}: that pair tears the
 * container down at the end of each {@code *IT} class, leaving Spring's cached
 * {@code ApplicationContext} (which still points at the now-dead port) orphaned for every
 * following class. Instead, each container is a static singleton, started once. MongoDB is here
 * (not just in a read-model-specific base class) because once {@code spring-boot-starter-data-mongodb}
 * is on the classpath, any full-context {@code @SpringBootTest} - including ones that never touch
 * Mongo - needs a reachable {@code MongoClient} to start.
 */
@ActiveProfiles("test")
public abstract class AbstractAuthIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES_CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection
    protected static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    static {
        POSTGRES_CONTAINER.start();
        MONGO_CONTAINER.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    protected HttpHeaders headers() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders authHeadersForUser() {
        return authHeaders(JwtTestTokenFactory.userToken());
    }

    protected HttpHeaders authHeadersForAdmin() {
        return authHeaders(JwtTestTokenFactory.adminToken());
    }

    protected HttpHeaders authHeadersForInvalidAudience() {
        return authHeaders(JwtTestTokenFactory.tokenWithInvalidAudience());
    }

    private HttpHeaders authHeaders(final String token) {
        final HttpHeaders authHeaders = headers();
        authHeaders.setBearerAuth(token);
        return authHeaders;
    }

    protected <T> HttpEntity<T> request(final T body, final HttpHeaders requestHeaders) {
        return new HttpEntity<>(body, requestHeaders);
    }

    protected HttpEntity<Void> request(final HttpHeaders requestHeaders) {
        return new HttpEntity<>(requestHeaders);
    }
}
