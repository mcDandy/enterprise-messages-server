package cz.upce.fei.ems.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {
    private static final String PATH = "/test/endpoint";

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ApiErrorWriter(mock(tools.jackson.databind.ObjectMapper.class)));
        request = new MockHttpServletRequest();
        request.setRequestURI(PATH);
    }

    @Test
    void validationHandlerSupportsBothSpringValidationExceptions() throws Exception {
        var binding = new BeanPropertyBindingResult(new Fixture(), "request");
        binding.rejectValue("username", "NotBlank", "must not be blank");
        Method method = Fixture.class.getDeclaredMethod("argument", String.class);
        MethodArgumentNotValidException invalid = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), binding);

        assertError(handler.handleValidation(invalid, request), 400, "Request validation failed.");
        assertEquals("must not be blank", ((Map<?, ?>) body(handler.handleValidation(invalid, request))
                .getProperties().get("errors")).get("username"));
        assertError(handler.handleValidation(new BindException(binding), request), 400,
                "Request validation failed.");
    }

    @Test
    void methodValidationHandlerReturnsBadRequest() {
        assertError(handler.handleMethodValidation(mock(HandlerMethodValidationException.class), request),
                400, "Request validation failed.");
    }

    @Test
    void constraintViolationHandlerReturnsBadRequest() {
        assertError(handler.handleConstraintViolation(new ConstraintViolationException("invalid value", Set.of()), request),
                400, "invalid value");
    }

    @Test
    void unreadableRequestHandlerCoversMalformedBodyAndMissingParameter() {
        assertError(handler.handleUnreadableRequest(new HttpMessageNotReadableException("bad json", null), request),
                400, "Request body is missing or malformed.");
        assertError(handler.handleUnreadableRequest(
                new MissingServletRequestParameterException("page", "int"), request),
                400, "Required parameter 'page' is missing.");
    }

    @Test
    void typeMismatchHandlerReturnsBadRequest() {
        var exception = new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null, null);
        assertError(handler.handleTypeMismatch(exception, request), 400, "Invalid value for parameter 'page'.");
    }

    @Test
    void unsupportedMethodHandlerReturns405() {
        assertError(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"), request),
                405, "HTTP method is not supported for this endpoint.");
    }

    @Test
    void missingResourceHandlerReturns404() {
        assertError(handler.handleNotFound(request), 404, "Resource not found.");
    }

    @Test
    void illegalArgumentHandlerReturnsBadRequestAndFallbackForBlankMessage() {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException("invalid input"), request),
                400, "invalid input");
        assertError(handler.handleIllegalArgument(new IllegalArgumentException(), request), 400, "Bad request.");
    }

    @Test
    void illegalStateHandlerReturnsConflictAndFallbackForBlankMessage() {
        assertError(handler.handleIllegalState(new IllegalStateException("already exists"), request),
                409, "already exists");
        assertError(handler.handleIllegalState(new IllegalStateException(), request), 409, "Conflict.");
    }

    @Test
    void responseStatusHandlerPreservesStatusAndReason() {
        assertError(handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"), request), 404, "missing");
        assertError(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.CONFLICT), request),
                409, "Conflict");
    }

    @Test
    void authenticationHandlerReturns401() {
        assertError(handler.handleAuthentication(request), 401, "Authentication is required or invalid.");
    }

    @Test
    void accessDeniedHandlerCoversSpringAndJavaSecurityExceptions() {
        assertError(handler.handleAccessDenied(request), 403, "Access denied.");
        assertMapped(AccessDeniedException.class, "handleAccessDenied");
        assertMapped(SecurityException.class, "handleAccessDenied");
        assertMapped(AuthenticationException.class, "handleAuthentication");
    }

    @Test
    void unexpectedExceptionHandlerReturnsSanitized500() {
        assertError(handler.handleUnexpected(new RuntimeException("internal detail"), request),
                500, "Internal server error.");
    }

    @Test
    void everyPublicHandlerHasAnExceptionHandlerMapping() {
        int mappedMethods = 0;
        for (Method method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            if (method.getName().startsWith("handle")) {
                assertNotNull(method.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class),
                        method.getName() + " must be registered with Spring");
                mappedMethods++;
            }
        }
        assertEquals(13, mappedMethods);
        assertMapped(HttpMessageNotReadableException.class, "handleUnreadableRequest");
        assertMapped(MissingServletRequestParameterException.class, "handleUnreadableRequest");
        assertMapped(NoResourceFoundException.class, "handleNotFound");
        assertMapped(Exception.class, "handleUnexpected");
    }

    private void assertMapped(Class<? extends Throwable> exception, String expectedHandler) {
        Method mapped = java.util.Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(expectedHandler))
                .findFirst().orElseThrow();
        var annotation = mapped.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
        assertTrue(Set.of(annotation.value()).contains(exception),
                exception.getSimpleName() + " should map to " + expectedHandler);
    }

    private void assertError(ResponseEntity<ProblemDetail> response, int status, String message) {
        assertEquals(status, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(status, response.getBody().getStatus());
        assertEquals(message, response.getBody().getDetail());
        assertEquals(PATH, response.getBody().getInstance().toString());
    }

    private ProblemDetail body(ResponseEntity<ProblemDetail> response) {
        return response.getBody();
    }

    static class Fixture {
        private String username;

        void argument(String value) {}

        public String getUsername() { return username; }

        public void setUsername(String username) { this.username = username; }
    }
}
