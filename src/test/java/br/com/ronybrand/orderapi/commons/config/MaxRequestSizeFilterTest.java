package br.com.ronybrand.orderapi.commons.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MaxRequestSizeFilterTest {

    private final MaxRequestSizeFilter filter = new MaxRequestSizeFilter(1_000L);

    @Test
    void doFilterInternal_ShouldContinueChain_WhenContentLengthIsWithinLimit() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(500L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilterInternal_ShouldReject413_WhenContentLengthExceedsLimit() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(1_001L);

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(org.mockito.ArgumentMatchers.eq(HttpStatus.PAYLOAD_TOO_LARGE.value()),
                org.mockito.ArgumentMatchers.anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldContinueChain_WhenContentLengthIsUnknown() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        when(request.getContentLengthLong()).thenReturn(-1L);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
