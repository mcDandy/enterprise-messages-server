package cz.upce.fei.ems.backend.config;

import cz.upce.fei.ems.backend.websocket.ChatBinaryWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatBinaryWebSocketHandler chatBinaryWebSocketHandler;

    public WebSocketConfig(ChatBinaryWebSocketHandler chatWebSocketHandler) {
        this.chatBinaryWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatBinaryWebSocketHandler, "/ws/chat")
                .setAllowedOrigins("*"); // todo limit to curent ip/domain via .env or variable
    }
}
