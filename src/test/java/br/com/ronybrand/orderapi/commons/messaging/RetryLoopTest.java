package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class RetryLoopTest {

    private static final class RetryableException extends RuntimeException {
        RetryableException(final String message) {
            super(message);
        }
    }

    @Test
    void run_ShouldSucceedOnFirstAttempt_WhenActionSucceeds() {
        final AtomicInteger calls = new AtomicInteger();

        RetryLoop.run(RetryableException.class, 3, Duration.ofMillis(1), 2.0,
                calls::incrementAndGet,
                (attempt, maxAttempts, backoffMs, e) -> { throw new AssertionError("should not retry"); },
                (attempts, e) -> { throw new AssertionError("should not exhaust"); });

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void run_ShouldRetryThenSucceed_WhenActionFailsOnceThenSucceeds() {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger retries = new AtomicInteger();

        RetryLoop.run(RetryableException.class, 3, Duration.ofMillis(1), 2.0,
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new RetryableException("transient");
                    }
                },
                (attempt, maxAttempts, backoffMs, e) -> retries.incrementAndGet(),
                (attempts, e) -> { throw new AssertionError("should not exhaust"); });

        assertThat(calls.get()).isEqualTo(2);
        assertThat(retries.get()).isEqualTo(1);
    }

    @Test
    void run_ShouldRejectWithoutRequeue_AfterExhaustingRetries() {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger exhaustedAttempts = new AtomicInteger();

        assertThatThrownBy(() -> RetryLoop.run(RetryableException.class, 2, Duration.ofMillis(1), 2.0,
                () -> {
                    calls.incrementAndGet();
                    throw new RetryableException("always fails");
                },
                (attempt, maxAttempts, backoffMs, e) -> { },
                (attempts, e) -> exhaustedAttempts.set(attempts)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(exhaustedAttempts.get()).isEqualTo(3);
    }

    @Test
    void run_ShouldPropagateImmediately_WhenExceptionIsNotTheRetryableType() {
        final IllegalStateException nonRetryable = new IllegalStateException("boom");

        assertThatThrownBy(() -> RetryLoop.run(RetryableException.class, 3, Duration.ofMillis(1), 2.0,
                () -> { throw nonRetryable; },
                (attempt, maxAttempts, backoffMs, e) -> { throw new AssertionError("should not retry"); },
                (attempts, e) -> { throw new AssertionError("should not exhaust"); }))
                .isSameAs(nonRetryable);
    }
}
