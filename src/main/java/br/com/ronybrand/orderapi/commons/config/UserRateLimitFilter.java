package br.com.ronybrand.orderapi.commons.config;

import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per authenticated user (the JWT subject), applied <em>after</em> authentication
 * (see {@link SecurityConfig}). The address-based {@link RateLimitFilter} runs first as a coarse ceiling against
 * floods and anonymous callers; limiting only by address would make everyone behind one proxy/NAT share a bucket,
 * and a user changing address would escape theirs.
 *
 * <p>Mutating requests (everything but GET/HEAD/OPTIONS, except {@code .../search} which is a read sent as POST)
 * also count against a stricter write budget: each order transition publishes an event, and a write should not
 * share a bucket with a read. Single instance only, like the address filter.
 *
 * <p>Not a {@code @Component}: Spring Boot would register every {@code Filter} bean in the servlet chain
 * <em>before</em> Spring Security, where there is no authentication yet. It is built and added in
 * {@link SecurityConfig#securityFilterChain}.
 */
public class UserRateLimitFilter extends OncePerRequestFilter {

    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String HEALTH_PATH = "/actuator/health";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String SEARCH_SUFFIX = "/search";

    private final int maxRequestsPerWindow;
    private final int maxWritesPerWindow;
    private final long windowSeconds;
    private final Cache<String, AtomicInteger> counters;

    public UserRateLimitFilter(final int maxRequestsPerWindow, final int maxWritesPerWindow, final long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.maxWritesPerWindow = maxWritesPerWindow;
        this.windowSeconds = windowSeconds;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken || isHealthProbe(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String user = authentication.getName();
        final boolean overall = hit("user:" + user) > maxRequestsPerWindow;
        final boolean writeBudget = isWrite(request) && hit("write:" + user) > maxWritesPerWindow;
        if (overall || writeBudget) {
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(windowSeconds));
            JsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int hit(final String key) {
        return counters.asMap().computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    private static boolean isWrite(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return !READ_METHODS.contains(request.getMethod()) && (uri == null || !uri.endsWith(SEARCH_SUFFIX));
    }

    private static boolean isHealthProbe(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return uri != null && uri.startsWith(request.getContextPath() + HEALTH_PATH);
    }
}
