package cz.upce.fei.ems.backend.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON fallback for errors raised by the servlet container outside Spring MVC handling. */
@RestController
public class JsonErrorController implements ErrorController {
    private final ApiErrorWriter errors;

    public JsonErrorController(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = statusAttribute instanceof Integer code ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus status = HttpStatus.resolve(statusCode);
        String message = statusCode == HttpStatus.NOT_FOUND.value() ? "Resource not found." : "Request failed.";
        Object errorPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = errorPath instanceof String value ? value : request.getRequestURI();

        Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Throwable cause = exceptionAttribute instanceof Throwable throwable ? throwable : null;
        return ResponseEntity.status(statusCode).body(errors.create(HttpStatusCode.valueOf(statusCode),
                message, path, cause));
    }
}
