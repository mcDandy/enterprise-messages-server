package cz.upce.fei.ems.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficGuardTest {
    @Test
    void enforcesPerClientHttpAndWebSocketRates() {
        TrafficGuard guard = new TrafficGuard(2, 1, 2);
        for (int i = 0; i < 240; i++) assertTrue(guard.allowHttpRequest("http-test"));
        assertFalse(guard.allowHttpRequest("http-test"));

        for (int i = 0; i < 240; i++) assertTrue(guard.allowWebSocketMessage("ws-test"));
        assertFalse(guard.allowWebSocketMessage("ws-test"));

        for (int i = 0; i < 30; i++) assertTrue(guard.allowWebSocketHandshake("handshake-test"));
        assertFalse(guard.allowWebSocketHandshake("handshake-test"));
    }

    @Test
    void boundsConcurrentRequestsWebSocketWorkAndConnections() {
        TrafficGuard guard = new TrafficGuard(2, 1, 2);

        assertTrue(guard.tryEnterHttp());
        assertTrue(guard.tryEnterHttp());
        assertFalse(guard.tryEnterHttp());
        guard.leaveHttp();
        assertTrue(guard.tryEnterHttp());
        guard.leaveHttp();
        guard.leaveHttp();

        assertTrue(guard.tryEnterWebSocketWork());
        assertFalse(guard.tryEnterWebSocketWork());
        guard.leaveWebSocketWork();
        assertTrue(guard.tryEnterWebSocketWork());
        guard.leaveWebSocketWork();

        assertTrue(guard.tryOpenWebSocket());
        assertTrue(guard.tryOpenWebSocket());
        assertFalse(guard.tryOpenWebSocket());
        guard.closeWebSocket();
        assertTrue(guard.tryOpenWebSocket());
        guard.closeWebSocket();
        guard.closeWebSocket();
    }
}
