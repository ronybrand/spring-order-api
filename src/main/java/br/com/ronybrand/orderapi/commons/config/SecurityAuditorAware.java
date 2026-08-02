package br.com.ronybrand.orderapi.commons.config;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The application's single {@link AuditorAware}, used by {@code @CreatedBy}/
 * {@code @LastModifiedBy} (Spring Data JPA Auditing) on every entity. Resolves the authenticated
 * user; outside an authenticated context (e.g. an internal/batch job), falls back to
 * {@code "system"}. Build this bean once here and reuse it on every new entity - don't
 * reimplement this resolution per domain.
 */
@Component
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM_USER = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(SYSTEM_USER);
        }
        return Optional.of(authentication.getName());
    }
}
