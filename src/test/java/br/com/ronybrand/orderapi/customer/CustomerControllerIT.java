package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ErrorResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class CustomerControllerIT extends AbstractAuthIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void cleanUp() {
        customerRepository.deleteAll();
    }

    @Test
    void create_ShouldReturn201AndPersist_WhenCallerIsAdmin() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-0001", "AB123456", "ada@example.com");

        final ResponseEntity<Void> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(request, authHeadersForAdmin()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(customerRepository.existsByTaxId("TAX-0001")).isTrue();
    }

    @Test
    void create_ShouldReturn403_WhenCallerIsNotAdmin() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-0002", null, "ada@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(customerRepository.existsByTaxId("TAX-0002")).isFalse();
    }

    @Test
    void create_ShouldReturn401_WhenUnauthenticated() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-0003", null, "ada@example.com");

        final ResponseEntity<Void> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(request, headers()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_ShouldReturn401_WhenAudienceIsInvalid() {
        final CustomerRequestDto request = new CustomerRequestDto("Ada Lovelace", "TAX-0006", null, "ada@example.com");

        final ResponseEntity<Void> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(request, authHeadersForInvalidAudience()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_ShouldReturn409_WhenTaxIdAlreadyExists() {
        final CustomerRequestDto first = new CustomerRequestDto("Ada Lovelace", "TAX-0004", null, "ada@example.com");
        restTemplate.exchange("/customers", HttpMethod.POST, request(first, authHeadersForAdmin()), Void.class);
        final CustomerRequestDto duplicate = new CustomerRequestDto("Other Name", "TAX-0004", null, "other@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(duplicate, authHeadersForAdmin()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS.getCode());
    }

    @Test
    void create_ShouldReturn400_WhenRequiredFieldIsMissing() {
        final CustomerRequestDto invalid = new CustomerRequestDto("", "TAX-0005", null, "ada@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers", HttpMethod.POST,
                request(invalid, authHeadersForAdmin()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
