package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import br.com.ronybrand.orderapi.commons.exception.ErrorResponseDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class CustomerControllerIT extends AbstractAuthIntegrationTest {

    @MockitoSpyBean
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

    @Test
    void findById_ShouldReturn200_WhenExists() {
        final Customer customer = customerRepository.save(
                Customer.builder().name("Ada Lovelace").taxId("TAX-0007").email("ada@example.com").build());

        final ResponseEntity<CustomerDto> response = restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.GET,
                request(authHeadersForUser()), CustomerDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(customer.getId());
    }

    @Test
    void findById_ShouldReturn404_WhenNotExists() {
        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers/" + UUID.randomUUID(), HttpMethod.GET,
                request(authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND_CUSTOMER.getCode());
    }

    @Test
    void findById_ShouldOnlyHitRepositoryOnce_WhenCalledTwice() {
        final Customer customer = customerRepository.save(
                Customer.builder().name("Ada Lovelace").taxId("TAX-0013").email("ada@example.com").build());

        restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.GET, request(authHeadersForUser()), CustomerDto.class);
        restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.GET, request(authHeadersForUser()), CustomerDto.class);

        verify(customerRepository, times(1)).findById(customer.getId());
    }

    @Test
    void update_ShouldReturn204AndPersistChanges_WhenCallerIsAdmin() {
        final Customer customer = customerRepository.save(
                Customer.builder().name("Old Name").taxId("TAX-0008").email("old@example.com").build());
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-0008-NEW", null, "new@example.com");

        final ResponseEntity<Void> response = restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.PUT,
                request(request, authHeadersForAdmin()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getName()).isEqualTo("New Name");
    }

    @Test
    void update_ShouldReturn403_WhenCallerIsNotAdmin() {
        final Customer customer = customerRepository.save(
                Customer.builder().name("Old Name").taxId("TAX-0009").email("old@example.com").build());
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-0009", null, "new@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.PUT,
                request(request, authHeadersForUser()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void update_ShouldReturn404_WhenNotExists() {
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-0010", null, "new@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers/" + UUID.randomUUID(), HttpMethod.PUT,
                request(request, authHeadersForAdmin()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_ShouldReturn409_WhenTaxIdBelongsToAnotherCustomer() {
        customerRepository.save(Customer.builder().name("Other").taxId("TAX-0011").email("other@example.com").build());
        final Customer customer = customerRepository.save(
                Customer.builder().name("Old Name").taxId("TAX-0012").email("old@example.com").build());
        final CustomerRequestDto request = new CustomerRequestDto("New Name", "TAX-0011", null, "new@example.com");

        final ResponseEntity<ErrorResponseDto> response = restTemplate.exchange("/customers/" + customer.getId(), HttpMethod.PUT,
                request(request, authHeadersForAdmin()), ErrorResponseDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_CUSTOMER_TAXID_EXISTS.getCode());
    }
}
