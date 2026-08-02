package br.com.ronybrand.orderapi.commons.exception;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Shared "find by id or throw 404" - the same one-liner every domain service otherwise repeats
 * (extracted once a second occurrence appeared, see the {@code spring-feature} skill's guidance
 * on shared helpers over per-domain re-implementation).
 */
public final class RepositoryLookups {

    private RepositoryLookups() {
    }

    public static <T, ID> T getOrThrow(final JpaRepository<T, ID> repository, final ID id, final ErrorCode errorCode,
            final String message) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(message, errorCode));
    }
}
