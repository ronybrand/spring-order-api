package br.com.ronybrand.orderapi.commons.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LimitedBodyRequestTest {

    private static ServletInputStream streamOf(final int size) {
        final ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[size]);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bytes.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() {
                return bytes.read();
            }
        };
    }

    private static LimitedBodyRequest requestWithBody(final int size, final long limit) throws IOException {
        final HttpServletRequest delegate = Mockito.mock(HttpServletRequest.class);
        Mockito.when(delegate.getInputStream()).thenReturn(streamOf(size));
        return new LimitedBodyRequest(delegate, limit);
    }

    @Test
    void getInputStream_ShouldReadABodyWithinTheLimit() throws IOException {
        final LimitedBodyRequest request = requestWithBody(100, 100);

        assertThat(request.getInputStream().readAllBytes()).hasSize(100);
    }

    @Test
    void getInputStream_ShouldCutOffABodyThatPassesTheLimitWithoutAContentLength() throws IOException {
        final LimitedBodyRequest request = requestWithBody(101, 100);
        final ServletInputStream stream = request.getInputStream();

        assertThatThrownBy(stream::readAllBytes).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void getInputStream_ShouldCountBulkReadsToo() throws IOException {
        final LimitedBodyRequest request = requestWithBody(500, 100);
        final ServletInputStream stream = request.getInputStream();
        final byte[] buffer = new byte[64];

        assertThat(stream.read(buffer, 0, 64)).isEqualTo(64);
        assertThatThrownBy(() -> stream.read(buffer, 0, 64)).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void getReader_ShouldBeLimitedAsWell() throws IOException {
        final HttpServletRequest delegate = Mockito.mock(HttpServletRequest.class);
        Mockito.when(delegate.getInputStream()).thenReturn(streamOf(500));
        final LimitedBodyRequest request = new LimitedBodyRequest(delegate, 100);

        assertThatThrownBy(() -> request.getReader().transferTo(java.io.Writer.nullWriter()))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }
}
