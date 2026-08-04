package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Fail-fast guard for the checklist item "Swagger/OpenAPI continua desabilitado fora do profile
 * dev" (AGENTS.md). That guarantee today rests on {@code application.yml} - the base config
 * loaded in every profile, including production - shipping with springdoc disabled, and only
 * {@code application-dev.yml} re-enabling it. Nothing stopped a future edit to the base file from
 * silently flipping that default; this test reads the actual base file (not a mock) and breaks
 * the build the moment it does.
 */
class SwaggerDisabledByDefaultTest {

    @Test
    void baseApplicationYml_ShouldDisableSpringdoc_SoProductionNeverExposesItByAccident() throws Exception {
        final List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
        final PropertySource<?> source = sources.get(0);

        assertThat(source.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(source.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }
}
