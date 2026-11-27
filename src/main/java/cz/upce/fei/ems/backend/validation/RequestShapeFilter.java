package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/** Rejects ambiguous HTTP inputs before security and MVC bind request parameters. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RequestShapeFilter extends OncePerRequestFilter {
    private static final int MAX_REQUEST_TARGET_BYTES = 4 * 1024;
    private static final Set<String> SINGLETON_HEADERS = Set.of(
            "authorization", "content-type", "content-encoding", "content-length", "transfer-encoding",
            "host", "origin", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
            "upgrade", "sec-websocket-key", "sec-websocket-version");

    private final ApiErrorWriter errors;

    public RequestShapeFilter(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requestTargetBytes(request) > MAX_REQUEST_TARGET_BYTES) {
            reject(request, response, HttpStatus.URI_TOO_LONG, "Request URL is too long.");
            return;
        }
        if (hasDuplicateSingletonHeader(request)) {
            reject(request, response, HttpStatus.BAD_REQUEST, "Duplicate request header is not allowed.");
            return;
        }
        if (request.getHeader("Content-Length") != null && request.getHeader("Transfer-Encoding") != null) {
            reject(request, response, HttpStatus.BAD_REQUEST, "Conflicting request framing headers.");
            return;
        }
        if (!hasValidBodyEncoding(request, response)) return;
        try {
            if (hasDuplicateParameters(request)) {
                reject(request, response, HttpStatus.BAD_REQUEST, "Duplicate request parameters are not allowed.");
                return;
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            reject(request, response, HttpStatus.BAD_REQUEST, "Request parameters are malformed or exceed limits.");
            return;
        }
        chain.doFilter(request, response);
    }

    private int requestTargetBytes(HttpServletRequest request) {
        String target = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null) target += "?" + query;
        return target.getBytes(StandardCharsets.UTF_8).length;
    }

    private boolean hasDuplicateSingletonHeader(HttpServletRequest request) {
        for (String name : SINGLETON_HEADERS) {
            Enumeration<String> values = request.getHeaders(name);
            if (values != null && values.hasMoreElements()) {
                values.nextElement();
                if (values.hasMoreElements()) return true;
            }
        }
        return false;
    }

    private boolean hasValidBodyEncoding(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String contentEncoding = request.getHeader("Content-Encoding");
        if (contentEncoding != null && !contentEncoding.isBlank()
                && !contentEncoding.trim().equalsIgnoreCase("identity")) {
            reject(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Compressed request bodies are not supported.");
            return false;
        }

        String method = request.getMethod().toUpperCase(Locale.ROOT);
        boolean bodylessEndpoint = isBodylessPost(request, method);
        boolean hasBody = request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
        if (bodylessEndpoint) {
            if (hasBody) {
                reject(request, response, HttpStatus.BAD_REQUEST, "This endpoint does not accept a request body.");
                return false;
            }
            return true;
        }
        boolean bodyCapableMethod = Set.of("POST", "PUT", "PATCH").contains(method);
        if (!hasBody && !bodyCapableMethod) return true;
        if (!Set.of("POST", "PUT", "PATCH").contains(method)) {
            reject(request, response, HttpStatus.BAD_REQUEST, "This HTTP method does not accept a request body.");
            return false;
        }

        String contentType = request.getContentType();
        if (contentType == null) {
            reject(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json.");
            return false;
        }

        final MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            reject(request, response, HttpStatus.BAD_REQUEST, "Content-Type header is malformed.");
            return false;
        }
        boolean json = MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                || ("application".equalsIgnoreCase(mediaType.getType())
                && mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json"));
        if (!json) {
            reject(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be JSON.");
            return false;
        }
        try {
            if (mediaType.getCharset() != null && !StandardCharsets.UTF_8.equals(mediaType.getCharset())) {
                reject(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JSON request bodies must use UTF-8.");
                return false;
            }
        } catch (IllegalArgumentException exception) {
            reject(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JSON request bodies must use UTF-8.");
            return false;
        }
        return true;
    }

    private boolean isBodylessPost(HttpServletRequest request, String method) {
        if (!"POST".equals(method)) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.matches("/api/admin/users/[^/]+/(ban|unban)");
    }

    private boolean hasDuplicateParameters(HttpServletRequest request) {
        return request.getParameterMap().values().stream().anyMatch(values -> values != null && values.length > 1);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String detail) throws IOException {
        errors.write(request, response, status, detail);
    }
}
