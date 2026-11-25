package cz.upce.fei.ems.backend.net.session;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class SessionRegistryTest {

    @Test
    void closingReplacedSessionDoesNotUnregisterCurrentSession() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession oldSession = mock(WebSocketSession.class);
        WebSocketSession currentSession = mock(WebSocketSession.class);

        org.mockito.Mockito.when(oldSession.getAttributes()).thenReturn(new HashMap<>());
        org.mockito.Mockito.when(currentSession.getAttributes()).thenReturn(new HashMap<>());
        registry.registerSession("alice", oldSession);
        registry.registerSession("alice", currentSession);

        registry.unregisterSession(oldSession);

        assertSame(currentSession, registry.getSession("alice"));
    }
}
