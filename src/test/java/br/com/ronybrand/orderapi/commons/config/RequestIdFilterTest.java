package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void doFilterInternal_ShouldReuseIncomingHeader_WhenItIsAValidUuid() throws Exception {
        final String incoming = UUID.randomUUID().toString();
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.when(request.getHeader(RequestIdFilter.HEADER_NAME)).thenReturn(incoming);

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(RequestIdFilter.HEADER_NAME, incoming);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ShouldGenerateNewId_WhenHeaderIsMissing() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.when(request.getHeader(RequestIdFilter.HEADER_NAME)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(anyString(), anyString());
    }

    @Test
    void doFilterInternal_ShouldGenerateNewId_WhenHeaderIsNotAValidUuid() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.when(request.getHeader(RequestIdFilter.HEADER_NAME)).thenReturn("not-a-uuid");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(RequestIdFilter.HEADER_NAME),
                org.mockito.ArgumentMatchers.argThat(value -> !"not-a-uuid".equals(value)));
    }

    @Test
    void doFilterInternal_ShouldClearMdc_AfterChainCompletes() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.when(request.getHeader(RequestIdFilter.HEADER_NAME)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
