package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.exception.ApiErrorWriter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Caps JSON and other request bodies, including chunked requests without Content-Length. */
@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final ApiErrorWriter errors;
    private final long maxBytes;

    public RequestBodyLimitFilter(ApiErrorWriter errors,
                                  @Value("${api.max-request-body-bytes:1048576}") long maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("api.max-request-body-bytes must be positive");
        this.errors = errors;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   jakarta.servlet.FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            errors.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large.");
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream limitedStream;

        private LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedStream == null) limitedStream = new LimitedServletInputStream(super.getInputStream(), limit);
            return limitedStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        private LimitedServletInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (count == limit) {
                int extra = delegate.read();
                if (extra < 0) return -1;
                throw new RequestBodyTooLargeException();
            }
            int value = delegate.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (count == limit) return read() < 0 ? -1 : 0;
            int allowed = (int) Math.min(length, limit - count);
            int read = delegate.read(bytes, offset, allowed);
            if (read > 0) count += read;
            return read;
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
    }

    public static final class RequestBodyTooLargeException extends IOException {
        public RequestBodyTooLargeException() { super("Request body exceeds configured limit"); }
    }
}
