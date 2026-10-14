package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UserRateLimitFilterTest {

    private static final long WINDOW = 60;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(final String user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static HttpServletRequest request(final String method, final String uri) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getContextPath()).thenReturn("/api");
        return request;
    }

    private static HttpServletResponse response(final StringWriter body) throws IOException {
        final HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return response;
    }

    @Test
    void doFilterInternal_ShouldLimitPerUser_NotPerAddress() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(2, 100, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        final HttpServletRequest read = request("GET", "/api/customers/1");

        authenticateAs("alice");
        filter.doFilterInternal(read, response(new StringWriter()), chain);
        filter.doFilterInternal(read, response(new StringWriter()), chain);
        final HttpServletResponse third = response(new StringWriter());
        filter.doFilterInternal(read, third, chain);
        authenticateAs("bob");
        filter.doFilterInternal(read, response(new StringWriter()), chain);

        verify(third).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternal_ShouldRejectWithTheStandardErrorBodyAndRetryAfter() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(1, 100, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        final StringWriter body = new StringWriter();
        final HttpServletResponse rejected = response(body);
        authenticateAs("alice");

        filter.doFilterInternal(request("GET", "/api/customers"), response(new StringWriter()), chain);
        filter.doFilterInternal(request("GET", "/api/customers"), rejected, chain);

        verify(rejected).setHeader("Retry-After", "60");
        verify(rejected).setContentType("application/json");
        assertThat(body.toString()).contains("\"code\":\"RATE-00\"");
    }

    @Test
    void doFilterInternal_ShouldGiveOrderWritesTheirOwnStricterLimit() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(100, 2, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        authenticateAs("alice");

        filter.doFilterInternal(request("POST", "/api/orders"), response(new StringWriter()), chain);
        filter.doFilterInternal(request("PATCH", "/api/orders/1/items/2"), response(new StringWriter()), chain);
        final HttpServletResponse thirdWrite = response(new StringWriter());
        filter.doFilterInternal(request("POST", "/api/orders/1/confirm"), thirdWrite, chain);
        final HttpServletResponse read = response(new StringWriter());
        filter.doFilterInternal(request("GET", "/api/orders/1"), read, chain);

        verify(thirdWrite).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(read, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void doFilterInternal_ShouldTreatSearchByPostAsARead() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(100, 1, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        authenticateAs("alice");

        for (int i = 0; i < 5; i++) {
            final HttpServletResponse search = response(new StringWriter());
            filter.doFilterInternal(request("POST", "/api/orders/search"), search, chain);
            verify(search, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @Test
    void doFilterInternal_ShouldCountCustomerWritesAsWritesToo() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(100, 1, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        authenticateAs("admin");

        filter.doFilterInternal(request("DELETE", "/api/customers/1"), response(new StringWriter()), chain);
        final HttpServletResponse second = response(new StringWriter());
        filter.doFilterInternal(request("PUT", "/api/customers/2"), second, chain);

        verify(second).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void doFilterInternal_ShouldLeaveAnonymousCallersToTheAddressFilter() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(1, 1, WINDOW);
        final FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request("GET", "/api/customers"), response(new StringWriter()), chain);
        }

        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternal_ShouldNeverLimitTheHealthProbe() throws ServletException, IOException {
        final UserRateLimitFilter filter = new UserRateLimitFilter(1, 1, WINDOW);
        final FilterChain chain = mock(FilterChain.class);
        authenticateAs("alice");

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request("GET", "/api/actuator/health/readiness"), response(new StringWriter()), chain);
        }

        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
