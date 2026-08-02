package br.com.ronybrand.orderapi.commons.config;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS origin comes from {@code app.security.cors.allowed-origins} (CSV, env
 * {@code CORS_ALLOWED_ORIGINS}), never hardcoded. Outside the {@code dev}/{@code test} profiles,
 * an empty list fails application startup instead of letting it come up with CORS effectively
 * open because of incomplete configuration.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") final String allowedOriginsCsv,
            final Environment environment) {
        final List<String> allowedOrigins = parse(allowedOriginsCsv);
        final boolean devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (CollectionUtils.isEmpty(allowedOrigins) && !devOrTest) {
            throw new IllegalStateException(
                    "app.security.cors.allowed-origins must not be empty outside the dev/test profiles");
        }

        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parse(final String allowedOriginsCsv) {
        if (StringUtils.isBlank(allowedOriginsCsv)) {
            return List.of();
        }
        return Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }
}
