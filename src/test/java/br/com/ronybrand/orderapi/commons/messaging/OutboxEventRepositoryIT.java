package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ronybrand.orderapi.AbstractAuthIntegrationTest;
import br.com.ronybrand.orderapi.TestSecurityConfig;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the {@code SKIP LOCKED} hint on {@link OutboxEventRepository#findClaimable} actually lets
 * two concurrent publishers claim disjoint batches instead of one blocking on the other's row
 * lock - the entire reason ADR 0006 requires the Hibernate-specific lock-timeout hint instead of a
 * plain {@code PESSIMISTIC_WRITE}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class OutboxEventRepositoryIT extends AbstractAuthIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static OutboxEvent pendingEvent(final LocalDateTime availableAt) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return OutboxEvent.builder().id(UUID.randomUUID()).eventType("OrderChangedEvent")
                .aggregateId(UUID.randomUUID()).exchangeName("orders.exchange").routingKey("orders.changed")
                .payload("{}").status(OutboxStatus.PENDING).attempts(0).availableAt(availableAt).createdAt(now)
                .build();
    }

    @Test
    void findClaimable_ShouldLetTwoConcurrentTransactionsClaimDisjointBatches_ViaSkipLocked()
            throws InterruptedException, ExecutionException, TimeoutException {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final OutboxEvent first = repository.save(pendingEvent(now.minusSeconds(2)));
        final OutboxEvent second = repository.save(pendingEvent(now.minusSeconds(1)));
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final CountDownLatch firstTransactionHoldingLock = new CountDownLatch(1);
        final CountDownLatch releaseFirstTransaction = new CountDownLatch(1);

        final Future<List<UUID>> firstClaim = executor.submit(() -> transactionTemplate.execute(status -> {
            final List<UUID> ids = repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, 1)).stream()
                    .map(OutboxEvent::getId).toList();
            firstTransactionHoldingLock.countDown();
            awaitUninterruptibly(releaseFirstTransaction);
            return ids;
        }));

        assertThat(firstTransactionHoldingLock.await(5, TimeUnit.SECONDS)).isTrue();

        final Future<List<UUID>> secondClaim = executor.submit(() -> transactionTemplate.execute(
                status -> repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, 2)).stream()
                        .map(OutboxEvent::getId).toList()));

        final List<UUID> secondResult = secondClaim.get(5, TimeUnit.SECONDS);
        releaseFirstTransaction.countDown();
        final List<UUID> firstResult = firstClaim.get(5, TimeUnit.SECONDS);

        assertThat(firstResult).containsExactly(first.getId());
        assertThat(secondResult)
                .as("SKIP LOCKED must let the second transaction claim the row the first one hasn't locked, "
                        + "without blocking on it")
                .containsExactly(second.getId());
    }

    @Test
    @Transactional
    void findClaimable_ShouldReclaimExpiredProcessingEvent_AfterLeaseElapses() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final OutboxEvent stuck = repository.save(pendingEvent(now.minusMinutes(10)));
        stuck.markProcessing(now.minusMinutes(10));
        repository.save(stuck);

        final List<OutboxEvent> claimable = repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, 10));

        assertThat(claimable).extracting(OutboxEvent::getId).containsExactly(stuck.getId());
    }

    @Test
    @Transactional
    void findClaimable_ShouldNotReturnRecentlyProcessingEvent_WithinItsLease() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final OutboxEvent inFlight = repository.save(pendingEvent(now.minusSeconds(30)));
        inFlight.markProcessing(now.minusSeconds(30));
        repository.save(inFlight);

        final List<OutboxEvent> claimable = repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, 10));

        assertThat(claimable).isEmpty();
    }

    @Test
    @Transactional
    void findClaimable_ShouldNotReturnPendingEvent_WhoseAvailableAtIsInTheFuture() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        repository.save(pendingEvent(now.plusMinutes(1)));

        final List<OutboxEvent> claimable = repository.findClaimable(now, now.minusMinutes(5), PageRequest.of(0, 10));

        assertThat(claimable).isEmpty();
    }

    @Test
    @Transactional
    void deletePublishedBefore_ShouldDeleteOnlyPublishedEventsOlderThanCutoff() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final OutboxEvent oldPublished = repository.save(publishedEvent(now.minusDays(10)));
        final OutboxEvent recentPublished = repository.save(publishedEvent(now.minusHours(1)));
        final OutboxEvent oldFailed = repository.save(failedEvent(now.minusDays(10)));

        final int deleted = repository.deletePublishedBefore(OutboxStatus.PUBLISHED.name(), now.minusDays(7), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(oldPublished.getId())).isEmpty();
        assertThat(repository.findById(recentPublished.getId())).isPresent();
        assertThat(repository.findById(oldFailed.getId())).isPresent();
    }

    @Test
    @Transactional
    void deletePublishedBefore_ShouldRespectLimit_WhenMoreEligibleRowsThanBatchSize() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        repository.save(publishedEvent(now.minusDays(10)));
        repository.save(publishedEvent(now.minusDays(10)));
        repository.save(publishedEvent(now.minusDays(10)));

        final int deleted = repository.deletePublishedBefore(OutboxStatus.PUBLISHED.name(), now.minusDays(7), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countByStatusIn(List.of(OutboxStatus.PUBLISHED))).isEqualTo(1);
    }

    @Test
    @Transactional
    void deleteFailedBefore_ShouldDeleteOnlyFailedEventsOlderThanCutoff() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final OutboxEvent oldFailed = repository.save(failedEvent(now.minusDays(100)));
        final OutboxEvent recentFailed = repository.save(failedEvent(now.minusDays(1)));
        final OutboxEvent oldPublished = repository.save(publishedEvent(now.minusDays(100)));

        final int deleted = repository.deleteFailedBefore(OutboxStatus.FAILED.name(), now.minusDays(90), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(oldFailed.getId())).isEmpty();
        assertThat(repository.findById(recentFailed.getId())).isPresent();
        assertThat(repository.findById(oldPublished.getId())).isPresent();
    }

    @Test
    @Transactional
    void deleteFailedBefore_ShouldRespectLimit_WhenMoreEligibleRowsThanBatchSize() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        repository.save(failedEvent(now.minusDays(100)));
        repository.save(failedEvent(now.minusDays(100)));
        repository.save(failedEvent(now.minusDays(100)));

        final int deleted = repository.deleteFailedBefore(OutboxStatus.FAILED.name(), now.minusDays(90), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.countByStatusIn(List.of(OutboxStatus.FAILED))).isEqualTo(1);
    }

    private static OutboxEvent publishedEvent(final LocalDateTime publishedAt) {
        final OutboxEvent event = pendingEvent(publishedAt.minusSeconds(1));
        event.markPublished(publishedAt);
        return event;
    }

    private static OutboxEvent failedEvent(final LocalDateTime referenceTime) {
        final OutboxEvent event = pendingEvent(referenceTime.minusSeconds(1));
        for (int i = 0; i < 5; i++) {
            event.markRetry(referenceTime, "boom");
        }
        return event;
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
