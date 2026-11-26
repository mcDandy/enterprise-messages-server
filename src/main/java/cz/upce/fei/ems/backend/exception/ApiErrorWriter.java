package cz.upce.fei.ems.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Creates RFC 9457 problem details and writes JSON error responses for servlet-level failures.
 */

@Component
public class ApiErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorWriter.class);
    private final ObjectMapper objectMapper;

    /** Creates a writer using the application-configured JSON mapper. */
    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Builds a problem detail without exposing an underlying exception. */
    public ProblemDetail create(HttpStatusCode status, String detail, String path) {
        return create(status, detail, path, Map.of(), null);
    }

    /** Builds a problem detail and logs the cause when the status is a server error. */
    public ProblemDetail create(HttpStatusCode status, String detail, String path, Throwable cause) {
        return create(status, detail, path, Map.of(), cause);
    }

    /** Builds a problem detail with additional extension properties. */
    public ProblemDetail create(HttpStatusCode status, String detail, String path,
                                Map<String, ?> properties) {
        return create(status, detail, path, properties, null);
    }

    private ProblemDetail create(HttpStatusCode status, String detail, String path,
                                 Map<String, ?> properties, Throwable cause) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String title = knownStatus == null ? "HTTP Error" : knownStatus.getReasonPhrase();
        if (status.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            if (cause == null) {
                log.error("HTTP {} {} at {}: {}", status.value(), title, path, detail);
            } else {
                log.error("HTTP {} {} at {}: {}", status.value(), title, path, detail, cause);
            }
        } else if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            log.warn("HTTP {} {} at {}: {}", status.value(), title, path, detail);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        if (path != null && !path.isBlank()) problem.setInstance(URI.create(path));
        properties.forEach(problem::setProperty);
        return problem;
    }

    /** Writes a JSON error directly to the servlet response unless it has already been committed. */
    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), create(status, message, request.getRequestURI()));
    }
}
