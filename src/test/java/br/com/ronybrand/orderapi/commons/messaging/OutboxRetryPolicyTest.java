package br.com.ronybrand.orderapi.commons.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    void nextBackoff_ShouldDoubleEachAttempt_UpToTheCap() {
        assertThat(OutboxRetryPolicy.nextBackoff(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxRetryPolicy.nextBackoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRetryPolicy.nextBackoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxRetryPolicy.nextBackoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxRetryPolicy.nextBackoff(4)).isEqualTo(Duration.ofSeconds(16));
    }

    @Test
    void nextBackoff_ShouldNeverExceedTheSixtySecondCap() {
        assertThat(OutboxRetryPolicy.nextBackoff(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OutboxRetryPolicy.nextBackoff(10)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OutboxRetryPolicy.nextBackoff(100)).isEqualTo(Duration.ofSeconds(60));
    }
}
