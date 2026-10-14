package br.com.ronybrand.orderapi.commons.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MaxRequestSizeFilterTest {

    private final MaxRequestSizeFilter filter = new MaxRequestSizeFilter(1_000L);

    @Test
    void doFilterInternal_ShouldContinueChain_WhenContentLengthIsWithinLimit() throws ServletException, IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(500L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(HttpServletRequest.class), org.mockito.ArgumentMatchers.eq(response));
        verify(response, never()).setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    @Test
    void doFilterInternal_ShouldReject413_WhenContentLengthExceedsLimit() throws ServletException, IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(1_001L);

        final java.io.StringWriter body = new java.io.StringWriter();
        when(response.getWriter()).thenReturn(new java.io.PrintWriter(body));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        org.assertj.core.api.Assertions.assertThat(body.toString()).contains("\"code\":\"VALIDATION-12\"");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternal_ShouldContinueChain_WhenContentLengthIsUnknown() throws ServletException, IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(-1L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(HttpServletRequest.class), org.mockito.ArgumentMatchers.eq(response));
    }

    @Test
    void doFilterInternal_ShouldHandTheChainARequestThatEnforcesTheLimitWhileReading() throws ServletException, IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(-1L);

        filter.doFilterInternal(request, response, chain);

        final org.mockito.ArgumentCaptor<HttpServletRequest> passed = org.mockito.ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(passed.capture(), org.mockito.ArgumentMatchers.eq(response));
        org.assertj.core.api.Assertions.assertThat(passed.getValue()).isInstanceOf(LimitedBodyRequest.class);
    }
}
