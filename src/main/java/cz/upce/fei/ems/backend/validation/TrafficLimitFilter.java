package cz.upce.fei.ems.backend.validation;

import cz.upce.fei.ems.backend.config.TrafficGuard;
import cz.upce.fei.ems.backend.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrafficLimitFilter extends OncePerRequestFilter {
    private final TrafficGuard trafficGuard;
    private final ApiErrorWriter errors;

    public TrafficLimitFilter(TrafficGuard trafficGuard, ApiErrorWriter errors) {
        this.trafficGuard = trafficGuard;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!trafficGuard.allowHttpRequest(client)) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Request rate limit exceeded. Retry shortly.");
            return;
        }
        if (!trafficGuard.tryEnterHttp()) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Server is temporarily busy. Retry shortly.");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            trafficGuard.leaveHttp();
        }
    }
}
