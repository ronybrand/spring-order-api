package br.com.ronybrand.orderapi.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @AllArgsConstructor
    static class Fixture {
        private String name;
        @Sensitive
        private String taxId;
    }

    @Test
    void toString_ShouldMaskSensitiveField_AndKeepOthersVisible() {
        final String result = SensitiveDataMasker.toString(new Fixture("Ada Lovelace", "12345-AB"));

        assertThat(result)
                .contains("name=Ada Lovelace")
                .contains("taxId=***REDACTED***")
                .doesNotContain("12345-AB");
    }

    @Test
    void toString_ShouldRenderClassSimpleName() {
        final String result = SensitiveDataMasker.toString(new Fixture("Ada Lovelace", "12345-AB"));

        assertThat(result).startsWith("Fixture(");
    }
}
