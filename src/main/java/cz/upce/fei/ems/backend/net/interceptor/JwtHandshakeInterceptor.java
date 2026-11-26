package cz.upce.fei.ems.backend.net.interceptor;

import cz.upce.fei.ems.backend.service.JwtService;
import cz.upce.fei.ems.backend.config.TrafficGuard;
import cz.upce.fei.ems.backend.exception.ApiErrorWriter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.net.InetSocketAddress;

/**
 * Authenticates WebSocket handshakes and associates the session with its user.
 */

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TrafficGuard trafficGuard;
    private final ApiErrorWriter errors;

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String client = request.getRemoteAddress() instanceof InetSocketAddress address
                ? address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress()
                : request.getRemoteAddress() == null ? "unknown" : request.getRemoteAddress().toString();
        if (!trafficGuard.allowWebSocketHandshake(client)) {
            if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest
                    && response instanceof ServletServerHttpResponse servletResponse) {
                try {
                    errors.write(servletRequest.getServletRequest(), servletResponse.getServletResponse(),
                            HttpStatus.TOO_MANY_REQUESTS, "Too many WebSocket connection attempts. Retry shortly.");
                } catch (java.io.IOException ignored) {
                    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                }
            } else {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            }
            return false;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            if (authenticateToken(token, attributes)) {
                return true;
            }
        } catch (Exception e) {
            // Invalid or expired credentials are rejected below.
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
            return authHeader.substring(7);
        }

        if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        return null;
    }

    private boolean authenticateToken(String token, Map<String, Object> attributes) {
        String username = jwtService.extractUsername(token);
        if (username == null) {
            return false;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            return false;
        }
        attributes.put("username", username);
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}
