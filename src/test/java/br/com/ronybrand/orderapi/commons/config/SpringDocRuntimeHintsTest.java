package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

class SpringDocRuntimeHintsTest {

    private final SpringDocRuntimeHints registrar = new SpringDocRuntimeHints();

    @Test
    void registerHints_ShouldRegisterReflectionEntry_ForKnownSpringDocProvider() {
        final RuntimeHints hints = new RuntimeHints();

        registrar.registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().typeHints())
                .anyMatch(typeHint -> typeHint.getType()
                        .equals(TypeReference.of("org.springdoc.core.providers.SpringWebProvider")));
    }

    @Test
    void registerHints_ShouldNotThrow_WhenClassIsNotOnClasspath() {
        final RuntimeHints hints = new RuntimeHints();

        assertThat(hints).isNotNull();
        registrar.registerHints(hints, getClass().getClassLoader());
    }
}
