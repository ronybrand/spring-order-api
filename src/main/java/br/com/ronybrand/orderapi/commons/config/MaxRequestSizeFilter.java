package br.com.ronybrand.orderapi.commons.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects, before the body reaches Jackson/Bean Validation, any request whose declared
 * {@code Content-Length} exceeds {@code app.security.max-request-size-bytes}. Defense in depth on
 * top of Tomcat's own limit (max-http-form-post-size): produces an explicit 413 earlier, without
 * depending on a generic container error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MaxRequestSizeFilter extends OncePerRequestFilter {

    private final long maxRequestSizeBytes;

    public MaxRequestSizeFilter(@Value("${app.security.max-request-size-bytes}") final long maxRequestSizeBytes) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestSizeBytes) {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value(), "Request body exceeds the maximum allowed size");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
