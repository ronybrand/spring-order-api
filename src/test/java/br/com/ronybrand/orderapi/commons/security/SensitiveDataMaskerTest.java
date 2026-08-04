package br.com.ronybrand.orderapi.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    record Fixture(String name, @Sensitive String taxId) {
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
