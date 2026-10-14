package br.com.ronybrand.orderapi.commons.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Counts the bytes as the body is read and cuts it off past the limit. The Content-Length check alone cannot
 * see a chunked upload (no Content-Length): without this the limit is bypassed by simply not declaring one.
 */
public class LimitedBodyRequest extends HttpServletRequestWrapper {

    private final long maxBytes;

    public LimitedBodyRequest(final HttpServletRequest request, final long maxBytes) {
        super(request);
        this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new LimitedStream(super.getInputStream(), maxBytes);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        final String encoding = getCharacterEncoding();
        final Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static final class LimitedStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        LimitedStream(final ServletInputStream delegate, final long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            final int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(final int bytes) throws IOException {
            bytesRead += bytes;
            if (bytesRead > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
