package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void corsConfigurationSource_ShouldThrowIllegalStateException_WhenOriginsEmptyOutsideDevOrTest() {
        final Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(Profiles.class))).thenReturn(false);

        assertThatThrownBy(() -> corsConfig.corsConfigurationSource("", environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corsConfigurationSource_ShouldNotThrow_WhenOriginsEmptyButProfileIsDevOrTest() {
        final Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(Profiles.class))).thenReturn(true);

        final CorsConfigurationSource source = corsConfig.corsConfigurationSource("", environment);

        assertThat(source).isNotNull();
    }

    @Test
    void corsConfigurationSource_ShouldParseCsvOrigins_TrimmingWhitespace() {
        final Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.any(Profiles.class))).thenReturn(false);

        final UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource)
                corsConfig.corsConfigurationSource(" http://localhost:3000 , http://example.com", environment);

        assertThat(source.getCorsConfigurations().get("/**").getAllowedOrigins())
                .containsExactly("http://localhost:3000", "http://example.com");
    }
}
