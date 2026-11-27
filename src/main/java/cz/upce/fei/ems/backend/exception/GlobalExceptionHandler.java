package cz.upce.fei.ems.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import cz.upce.fei.ems.backend.validation.RequestBodyLimitFilter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps application, validation, and Spring MVC exceptions to consistent problem-detail responses.
 */

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ApiErrorWriter errors;

    public GlobalExceptionHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex, HttpServletRequest request) {
        var bindingResult = ex instanceof MethodArgumentNotValidException invalid
                ? invalid.getBindingResult() : ((BindException) ex).getBindingResult();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        bindingResult.getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            fieldErrors.putIfAbsent(field, error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
        });
        ProblemDetail error = errors.create(HttpStatus.BAD_REQUEST, "Request validation failed.",
                request.getRequestURI(), Map.of("errors", Map.copyOf(fieldErrors)));
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(Exception ex, HttpServletRequest request) {
        if (hasCause(ex, RequestBodyLimitFilter.RequestBodyTooLargeException.class)) {
            return response(HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large.", request);
        }
        String message = ex instanceof MissingServletRequestParameterException missing
                ? "Required parameter '" + missing.getParameterName() + "' is missing."
                : "Request body is missing or malformed.";
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleConcurrentWrite(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                "The request conflicts with the current data state.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request content type is not supported.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_ACCEPTABLE, "Requested response format is not available.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, safeMessage(ex, "Bad request."), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, safeMessage(ex, "Conflict."), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus knownStatus = HttpStatus.resolve(ex.getStatusCode().value());
        String detail = ex.getReason() != null ? ex.getReason()
                : knownStatus == null ? "Request failed." : knownStatus.getReasonPhrase();
        return response(ex.getStatusCode(), detail, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Authentication is required or invalid.", request);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access denied.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(errors.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error.", request.getRequestURI(), ex));
    }

    private ResponseEntity<ProblemDetail> response(HttpStatusCode status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(errors.create(status, message, request.getRequestURI()));
    }

    private String safeMessage(RuntimeException ex, String fallback) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage();
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }
}
