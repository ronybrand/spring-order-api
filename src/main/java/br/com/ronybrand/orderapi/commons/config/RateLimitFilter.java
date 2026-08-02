package br.com.ronybrand.orderapi.commons.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit, partitioned by the caller's IP (first hop of {@code X-Forwarded-For},
 * or the direct remote address). The counter lives in a Caffeine {@code Cache} (not a plain
 * {@code Map}) to bound the memory used by distinct IPs over the application's lifetime: each
 * entry expires on its own at the end of the window, restarting the count. Documented as
 * single-instance only - does not coordinate the limit across replicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final int maxRequestsPerWindow;
    private final long windowSeconds;
    private final Cache<String, AtomicInteger> counters;

    public RateLimitFilter(
            @Value("${app.security.rate-limit.max-requests-per-window}") final int maxRequestsPerWindow,
            @Value("${app.security.rate-limit.window-seconds}") final long windowSeconds) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSeconds = windowSeconds;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final String key = resolveClientKey(request);
        final int count = counters.asMap().computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > maxRequestsPerWindow) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(windowSeconds));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(final HttpServletRequest request) {
        final String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
