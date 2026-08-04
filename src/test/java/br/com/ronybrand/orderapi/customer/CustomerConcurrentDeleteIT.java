package br.com.ronybrand.orderapi.customer;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import br.com.ronybrand.orderapi.order.OrderTestCleanupRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the two-sided pessimistic lock (DOMAIN.md §4.8) actually serializes
 * {@code CustomerService.delete} against a concurrent {@code OrderService.create} for the same
 * customer, instead of just exercising the lock methods sequentially (which would prove they
 * compile and return a row, but not that they block each other - the entire point of the fix).
 *
 * <p>Uses {@link CountDownLatch}es around the real blocking call, rather than racing two
 * independent HTTP requests and hoping they overlap: the delete-side transaction signals once it
 * holds the lock, the test confirms the create-side transaction is still blocked after a
 * generous margin, then releases the delete so both can complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class CustomerConcurrentDeleteIT extends AbstractAuthIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerTestCleanupRepository customerTestCleanupRepository;

    @Autowired
    private OrderTestCleanupRepository orderTestCleanupRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderTestCleanupRepository.deleteAllHard();
        customerTestCleanupRepository.deleteAllHard();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void findByIdForShare_ShouldBlock_UntilConcurrentFindByIdForUpdateTransactionCompletes()
            throws InterruptedException, ExecutionException, TimeoutException {
        final Customer customer = customerRepository.save(
                Customer.builder().name("Ada Lovelace").taxId("TAX-" + UUID.randomUUID().toString().substring(0, 8))
                        .email("ada@example.com").build());
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final CountDownLatch updateLockHeld = new CountDownLatch(1);
        final CountDownLatch releaseUpdateLock = new CountDownLatch(1);
        final CountDownLatch shareLockAcquired = new CountDownLatch(1);

        final Future<?> updateHolder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            customerRepository.findByIdForUpdate(customer.getId());
            updateLockHeld.countDown();
            awaitUninterruptibly(releaseUpdateLock);
        }));

        assertThat(updateLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        final Future<?> shareWaiter = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            customerRepository.findByIdForShare(customer.getId());
            shareLockAcquired.countDown();
        }));

        assertThat(shareLockAcquired.await(500, TimeUnit.MILLISECONDS))
                .as("FOR SHARE must block while a concurrent transaction holds FOR UPDATE on the same row")
                .isFalse();

        releaseUpdateLock.countDown();
        updateHolder.get(5, TimeUnit.SECONDS);

        assertThat(shareLockAcquired.await(5, TimeUnit.SECONDS))
                .as("FOR SHARE must proceed once the FOR UPDATE transaction commits")
                .isTrue();
        shareWaiter.get(5, TimeUnit.SECONDS);
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch to be released");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
