package br.com.ronybrand.orderapi.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SensitiveFieldsModuleTest {

    record Fixture(String name, @Sensitive String taxId) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new SensitiveFieldsModule());

    @Test
    void writeValueAsString_ShouldMaskSensitiveProperty() throws JsonProcessingException {
        final String json = objectMapper.writeValueAsString(new Fixture("Ada Lovelace", "12345-AB"));

        assertThat(json).contains("\"name\":\"Ada Lovelace\"")
                .contains("\"taxId\":\"***REDACTED***\"")
                .doesNotContain("12345-AB");
    }
}
