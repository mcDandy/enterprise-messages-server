package cz.upce.fei.ems.backend.net.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active user sessions and channel subscriptions for real-time delivery.
 */

@Slf4j
@Component
public class SessionRegistry {

    // username -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // channelId -> Set<username>
    private final Map<Long, Set<String>> channelMembers = new ConcurrentHashMap<>();

    /**
     * Registers the user’s current session and closes any previously registered session for that user.
     *
     * @param username authenticated session owner
     * @param session newly established WebSocket session
     */
    public void registerSession(String username, WebSocketSession session) {
        WebSocketSession previous = activeSessions.put(username, session);
        session.getAttributes().put("username", username);
        if (previous != null && previous != session && previous.isOpen()) {
            try {
                previous.close(CloseStatus.GOING_AWAY);
            } catch (IOException e) {
                log.debug("Could not close superseded WebSocket session for {}: {}", username, e.getMessage());
            }
        }
    }

    @SuppressWarnings("resource") // The WebSocket handler owns the session lifecycle.
    /** Removes a session only if it is still the registered session for its username. */
    public void unregisterSession(WebSocketSession session) {
        if (session.getAttributes().get("username") instanceof String username) {
            if (activeSessions.remove(username, session)) {
                channelMembers.values().forEach(members -> members.remove(username));
            }
        }
    }

    /** @return the registered session, or {@code null} when the user has no active session */
    public WebSocketSession getSession(String username) {
        return activeSessions.get(username);
    }

    /** @return whether the user has a registered, open WebSocket session */
    public boolean isUserOnline(String username) {
        WebSocketSession session = activeSessions.get(username);
        return session != null && session.isOpen();
    }

    /** Adds a connected user to the in-memory broadcast set for a channel. */
    public void joinChannel(Long channelId, String username) {
        channelMembers.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(username);
    }

    /** Removes a user from a channel broadcast set without affecting other channels. */
    public void leaveChannel(Long channelId, String username) {
        Set<String> members = channelMembers.get(channelId);
        if (members != null) {
            members.remove(username);
        }
    }

    /** @return open sessions currently subscribed to the channel */
    public Set<WebSocketSession> getSessionsInChannel(Long channelId) {
        Set<String> usernames = channelMembers.getOrDefault(channelId, Set.of());
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

        for (String username : usernames) {
            WebSocketSession session = activeSessions.get(username);
            if (session != null && session.isOpen()) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    /** Closes and removes a user session and clears the user from every channel broadcast set. */
    public void removeAndCloseSession(String username) {
        WebSocketSession session = activeSessions.remove(username);
        if (session != null) {
            if (session.isOpen()) {
                try (session) {
                    log.debug("Closed WebSocket session for user {}", username);
                } catch (IOException e) {
                    log.warn("Failed to close WebSocket session for user {}: {}", username, e.getMessage(), e);
                }
            }
        }
        channelMembers.values().forEach(members -> members.remove(username));
    }
}
