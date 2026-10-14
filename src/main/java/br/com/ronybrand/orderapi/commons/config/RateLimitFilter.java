package br.com.ronybrand.orderapi.commons.config;

import org.slf4j.MDC;
import br.com.ronybrand.orderapi.commons.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit, partitioned by the caller's IP. The counter lives in a Caffeine
 * {@code Cache} (not a plain {@code Map}) to bound the memory used by distinct IPs over the
 * application's lifetime: each entry expires on its own at the end of the window, restarting the
 * count. Documented as single-instance only - does not coordinate the limit across replicas.
 *
 * <p>{@code X-Forwarded-For} is only trusted when the direct TCP peer ({@code getRemoteAddr()})
 * is itself in {@code app.security.rate-limit.trusted-proxies} (CSV) - e.g. the load balancer's
 * address. Otherwise any caller could set an arbitrary/rotating {@code X-Forwarded-For} value to
 * get a fresh bucket on every request and bypass the limit entirely.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String HEALTH_PATH = "/actuator/health";

    private final int maxRequestsPerWindow;
    private final long windowSeconds;
    private final Set<String> trustedProxies;
    private final Cache<String, AtomicInteger> counters;

    public RateLimitFilter(
            @Value("${app.security.rate-limit.max-requests-per-window}") final int maxRequestsPerWindow,
            @Value("${app.security.rate-limit.window-seconds}") final long windowSeconds,
            @Value("${app.security.rate-limit.trusted-proxies:}") final String trustedProxiesCsv) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSeconds = windowSeconds;
        this.trustedProxies = parseTrustedProxies(trustedProxiesCsv);
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
    }

    private static Set<String> parseTrustedProxies(final String trustedProxiesCsv) {
        if (StringUtils.isBlank(trustedProxiesCsv)) {
            return Set.of();
        }
        return Arrays.stream(trustedProxiesCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (isHealthProbe(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        final String key = resolveClientKey(request);
        final int count = counters.asMap().computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > maxRequestsPerWindow) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(RETRY_AFTER_HEADER, String.valueOf(windowSeconds));
            response.setContentType("application/json");
            response.getWriter().write(errorBody());
            return;
        }
        filterChain.doFilter(request, response);
    }

    // The orchestrator's liveness/readiness probe shares an address with whatever sits in front of
    // the app: rate limiting it would let ordinary traffic make a healthy instance look dead.
    private static boolean isHealthProbe(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return uri != null && uri.startsWith(request.getContextPath() + HEALTH_PATH);
    }

    // Same {message, code, params, requestId} contract as every other error; the request id was
    // put in the MDC by RequestIdFilter, which runs before this filter.
    private static String errorBody() {
        final String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return "{\"message\":\"" + ErrorCode.RATE_LIMIT_EXCEEDED.getDescription() + "\",\"code\":\""
                + ErrorCode.RATE_LIMIT_EXCEEDED.getCode() + "\",\"params\":{},\"requestId\":"
                + (requestId == null ? "null" : "\"" + requestId + "\"") + "}";
    }

    private String resolveClientKey(final HttpServletRequest request) {
        final String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            final String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
            if (StringUtils.isNotBlank(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
