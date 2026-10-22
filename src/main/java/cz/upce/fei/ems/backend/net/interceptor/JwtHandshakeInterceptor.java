package cz.upce.fei.ems.backend.net.interceptor;

import cz.upce.fei.ems.backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");

            if (token != null) {
                try {
                    if (jwtService.validateToken(token)) {
                        String username = jwtService.extractUsername(token);
                        attributes.put("username", username);
                        return true;
                    }
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                } catch (Exception e) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
            }
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        response.setStatusCode(HttpStatus.BAD_REQUEST);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No action needed after handshake
    }
}